package com.codedecorator.rider.minimal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.util.TextRange
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import com.intellij.util.concurrency.AppExecutorUtil
import javax.swing.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.*  // ваш остальной импорт
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.openapi.editor.Editor

import java.awt.event.KeyEvent											as _Key
import com.intellij.openapi.fileEditor.FileEditorManager 				as _FEM
import com.intellij.openapi.fileEditor.FileEditorManagerListener 		as _FEMListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent 			as _FEMEvent

import com.intellij.openapi.editor.markup.HighlighterTargetArea 		as HighlighterTargetArea

@Service(Service.Level.PROJECT)
public class DirectHighlighterComponent(private val project: Project) : Disposable 
{
    private data class Block (
        val startLine: Int,
        val endLine: Int,
        val startOffset: Int,
        val endOffset: Int,
        var priority: Int,
        var id: String = ""
    )

    private data class UpdateInfo (
        val state: Boolean,
        val editor: Editor?,
		
    )

	// Создаём очередь (можно один раз на плагин/компонент)
	private val redrawQueue = MergingUpdateQueue(
		"DHC/Redraw",          // имя очереди для логов
		50,                    // задержка (мс) — за это время сливаются повторы
		true,                  // runInEdt = true → выполнение в EDT
		null                   // диспозер (можно повесить на project)
	)

    private val LOG                         = com.intellij.openapi.diagnostic.Logger.getInstance(DirectHighlighterComponent::class.java)
    private val DOC_LISTENER_KEY 			= com.intellij.openapi.util.Key.create<Boolean>("DirectHighlighterComponent.DocListenerAttached")

    private val edtFactory 					= EditorFactory.getInstance()
    private val application 				= ApplicationManager.getApplication()


    private val lastHLValidKey 				= ConcurrentHashMap<Editor, Boolean>()
    private val lastHLUserKey 				= ConcurrentHashMap<Editor, Long>()
    private val lastHLTime 					= ConcurrentHashMap<Editor, Long>()

    private val lastHLDocChange 			= ConcurrentHashMap<Editor, Long>()

	private val inFlightFlag 				= ConcurrentHashMap<Editor, java.util.concurrent.atomic.AtomicBoolean>()
	private val pendingFlag 				= ConcurrentHashMap<Editor, java.util.concurrent.atomic.AtomicBoolean>()
	
    private val generationMap 				= ConcurrentHashMap<Editor, AtomicInteger>()
    private val runningFutures 				= ConcurrentHashMap<Editor, MutableList<Future<*>>>()
    private val activeHLs 					= ConcurrentHashMap<Editor, MutableList<RangeHighlighter>>()
    private val runningTasks 				= ConcurrentHashMap<Editor, ConcurrentHashMap<String, Future<*>>>()

    private val settings 					= SimpleSettings.getInstance()

    private val enabledRules				: List<HighlightRule> get() = settings.getEnabledRules()
	private var _updateInfo					: UpdateInfo 				= UpdateInfo(false, null)

    // private val overlayManager 			= OverlayManager(project)
 	// Overlay is disabled: provide a local no-op stub with the same methods used in this file.
    // This avoids changing dozens of call sites while completely disabling UI overlays.
    private val overlayManager = object {
        fun removeOverlayImmediate(editor: Editor?) {}
        fun prepareOverlay(editor: Editor?) {}
        fun prepareOverlay(editor: Editor?, totalRules: Int) {}
        fun setCompletedToTotal(editor: Editor?) {}
        fun showOrUpdateOverlay(editor: Editor?, text: String, inProgress: Boolean = false) {}
        fun hideOverlay(editor: Editor?) {}
        fun hideTaskOverlay(editor: Editor?, taskId: String) {}
        fun removeAllForEditor(editor: Editor?) {}
        fun showOrUpdateTaskOverlay(editor: Editor?, taskId: String, text: String) {}
        fun incrementCompleted(editor: Editor?) {}
        fun setTotalRules(editor: Editor?, total: Int) {}
    }
    
    private val editorFactoryListener 		= object : EditorFactoryListener
                                            {
                                                override fun editorCreated(event: EditorFactoryEvent) 
                                                {
                                                    val editor          = event.editor

                                                    if (editor.project == project) 
                                                    {
                                                        try { _AttachDocListener(editor) } catch (ex: Exception) { LOG.warn("[DHC]: editorFactoryListener.editorCreated -> AttachDocListener", ex) }
                                                    }
                                                }
                                                
                                                override fun editorReleased(event: EditorFactoryEvent) 
                                                {
                                                    val editor         = event.editor

                                                    try { _ClearHighlighters(editor) } catch (ex: Exception) { LOG.warn("[DHC]: editorFactoryListener.editorReleased -> _ClearHighlighters", ex) }

													// clear the marker so the editor can be re-attached later if needed
                                                    try  	
													{ 
														editor.putUserData(DOC_LISTENER_KEY, null) 
													} 
													catch (ex: Exception) 
													{ 
														LOG.warn("[DHC]: editorFactoryListener.editorReleased - clearing user data", ex) 
													}
                                                }
                                            }

	// _TriggerHighlight()
	// documentChanged: Срабатывает при вводе символов (нажатие клавиш, вставка), удалении, undo/redo и при любых вызовах API которые меняют текст (Document.replaceString, insertString и т.п.).					
	// commandFinished: Это вызывается при завершении «команды» (CommandProcessor) — например после paste, undo/redo, некоторых действий IDE или групповых операций.
    private val commandFinishedListener     = object : CommandListener 
                                            {
                                                override fun commandFinished(event: CommandEvent) 
                                                {
													var editor 			= _FEM.getInstance(project).selectedTextEditor
													val lastDocChange 	= lastHLDocChange[editor] ?: 0L
													val dt  			= System.currentTimeMillis() - lastDocChange
                                                    val isUndoRedo 		= event.commandName?.lowercase()?.contains("undo") == true || event.commandName?.lowercase()?.contains("redo") == true
                                                    
													if (dt > 120 || isUndoRedo) 
                                                    {
                                                        LOG.info		("[DHC]: commandFinishedListener: Redraw: Request (dt=$dt)")

														if (editor != null)
														{
															 requestRedraw(editor, isUndoRedo)
														}
                                                    } 
                                                }
                                            }

	// Cрабатывает всякий раз, когда в FileEditorManager меняется выбранный/активный редактор (смена вкладки/файла/окна).
 	private val editorSwiched 				= object : _FEMListener
                                            {
                                                override fun selectionChanged(event: _FEMEvent) 
                                                {
                                                    try 
                                                    {
                                                        // Use the current selected editor from the FileEditorManager (the field selectedTextEditor
                                                        // is captured at construction time and may be stale). This ensures we react to selection changes.
                                                        val currentSelected = _FEM.getInstance(project).selectedTextEditor

                                                        removeAllOverlaysExcept(currentSelected)

                                                        if (currentSelected == null) 
                                                        {
                                                            return
                                                        }

                                                        if (currentSelected.project == project) 
                                                        {
                                                            LOG.info        ("[DHC]: editorSwiched: Redraw: Applying...")
                                                            _Highlight_Apply (currentSelected)
                                                        }
                                                    } 
                                                    catch (ex: Exception) { LOG.warn("[DHC]: fileEditorSelectionListener.selectionChanged", ex) }
                                                }
                                            }

	// Только для важных клавиш (вставка, удаление, ввод, таб, ctrl+z/y/x/v) - обновляет время ввода пользователем
    private val keyEventDispatcher         = java.awt.KeyEventDispatcher { 
		
												e -> try 
												{
													if (e.id != java.awt.event.KeyEvent.KEY_PRESSED) 
													{
														return@KeyEventDispatcher false
													}

													val fem 					= com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
													val sel 					= fem.selectedTextEditor

													if (sel != null && sel.project == project) 
													{
														_UpdateLastUserKeyPress (sel, e.keyCode, e.modifiersEx)
													}
												} 
												catch (ex: Exception) 
												{
													LOG.warn 					("[DHC]: globalKeyDispatcher outer", ex)
												}

												false
											}

	private fun _UpdateLastUserKeyPress(e: Editor, keyCode: Int, modifiersEx: Int) 
	{
		// val _hash 						= try { e.document.hashCode() } catch (_: Exception) {0}
		// LOG.info 						("[DHC]: Keypress key=$keyCode, editor=${_hash}")

		val isCtrl 							= (modifiersEx and java.awt.event.KeyEvent.CTRL_DOWN_MASK) != 0
		lastHLValidKey[e] 					= when (keyCode) 
											{
												_Key.VK_DELETE, _Key.VK_BACK_SPACE, _Key.VK_ENTER, _Key.VK_TAB -> true
												_Key.VK_V, _Key.VK_X, _Key.VK_Z, _Key.VK_Y -> isCtrl
												else -> false
											}

		lastHLUserKey[e] 					= System.currentTimeMillis()
	}


    init 
    {
        edtFactory.addEditorFactoryListener (editorFactoryListener, project)

        this._AttachDocListeners            ()
        this._AttachCmdFinishedListener     ()
        this._AttachEditorSelectionListener ()
		this._AttachKeyListeners            ()

        LOG.info                             ("[DHC]: Plugin initialized")
    }

	private fun _AttachKeyListeners()
	{
		try 
		{
			try 
			{
				val kfm 					= java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()

				kfm.addKeyEventDispatcher	(keyEventDispatcher)

				LOG.info					("[DHC]: AttachKeyListeners - registered global KeyEventDispatcher")
			} 
			catch (ex: Exception) 
			{
				LOG.warn					("[DHC]: AttachKeyListeners - registering global KeyEventDispatcher", ex)
			}
		} 
		catch (ex: Exception) 
		{
			LOG.warn						("[DHC]: AttachKeyListeners - outer registering global KeyEventDispatcher", ex)
		}
	}

	private fun _ClearKeyListeners()
	{
        try 
		{
			val kfm 						= java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()

			kfm.removeKeyEventDispatcher	(keyEventDispatcher)

			LOG.info						("[DHC]: dispose - removed global KeyEventDispatcher")
        } 
		catch (ex: Exception) 
		{
            LOG.warn						("[DHC]: dispose - outer remove dispatcher", ex)
        }
	}

    private fun _AttachDocListeners()
    {
		LOG.info                             ("[DHC]: try AttachDocListeners")

        try 
        {
			var _editors 					= edtFactory.allEditors.filter { it.project == project };

			 for (e in _editors)
			 {
				try 
				{ 
					this._AttachDocListener(e) 
				} 
				catch (ex: Exception) 
				{ 
					LOG.warn("[DHC]: AttachDocListeners -> AttachDocListener (foreach)", ex) 
				} 
 			}
        } 
        catch (e: Exception) 
        {
            LOG.error          			 	 ("[DHC]: Failed to register DocumentListener", e)
        }
    }
	
    // Attach a DocumentListener to editor's document in an idempotent way
    public fun _AttachDocListener(editor: Editor) 
    {
    	LOG.info							("[DHC]: AttachDocListener")

		val _hash 							= try { editor.document.hashCode() } catch (_: Exception) {0}

        try 
        {
            // Idempotent attachment: skip if we've already attached to this editor
            try 
			{
                val already 				= editor.getUserData		(DOC_LISTENER_KEY)

                if (already == true) 
				{
                    LOG.info				("[DHC]: AttachDocListener - already attached for editor=${_hash}")
                    return
                }
            } 
			catch (ex: Exception) 
			{ 
				LOG.warn                   ("[DHC]: AttachDocListener - checking DOC_LISTENER_KEY", ex) 
			}

			val _documentListener 			= object : DocumentListener {
												// documentChanged: Срабатывает при вводе символов (нажатие клавиш, вставка), удалении, undo/redo и при любых вызовах API которые меняют текст (Document.replaceString, insertString и т.п.).
												override fun documentChanged(event: DocumentEvent)  
												{
													// 2.1 Быстрая локальная инвалидация на затронутых строках
        											invalidateHighlightsOnChangedLines(editor, event)

        											lastHLDocChange[editor] = System.currentTimeMillis()

													LOG.info			("[DHC]: documentListener: Redraw: Request...")
													requestRedraw		(editor)
												}
											}

            editor.document.addDocumentListener(_documentListener)

            try 
			{ 
				editor.putUserData         (DOC_LISTENER_KEY, true) 
			} 
			catch (ex: Exception) 
			{ 
				LOG.warn					("[DHC]: AttachDocListener - set DOC_LISTENER_KEY", ex) 
			}
        } 
        catch (ex: Exception) 
		{ 
			LOG.warn						("[DHC]: AttachDocListener", ex) 
		}
    }
    
    private fun _AttachCmdFinishedListener() {
		LOG.info                             ("[DHC]: try AttachCmdFinishedListener")

        try 
        {
            application.messageBus.connect(project).subscribe(CommandListener.TOPIC, commandFinishedListener)
        } 
        catch (e: Exception) 
        {
            LOG.error            			("[DHC]: Failed to register CommandFinishListener", e)
        }
    }

    private fun _AttachEditorSelectionListener() {
		LOG.info                            ("[DHC]: try AttachEditorSelectionListener")

        application.invokeLater {
 			
			try 
            {
				val _fem 					= _FEM.getInstance(project)
				
 				_fem.selectedTextEditor?.let {editor -> _Highlight_Apply(editor)}

                // Register via messageBus with project as parent Disposable to avoid deprecated FileEditorManager overloads
                application.messageBus.connect(project).subscribe(com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER, editorSwiched)
            } 
            catch (ex: Exception) { LOG.warn("[DHC]: _AttachEditorSelectionListener.invokeLater", ex) }
        }
    }

	private fun invalidateHighlightsOnChangedLines(editor: Editor, e: DocumentEvent) 
	{
		val doc 							= editor.document
		val startLine 						= doc.getLineNumber(e.offset)
		val endOffset 						= e.offset + (e.newLength.coerceAtLeast(0)) // new length при вставке, 0 при удалении
		val endLine 						= doc.getLineNumber(endOffset.coerceAtMost(doc.textLength))

		val lineStart 						= doc.getLineStartOffset(startLine)
		val lineEnd   						= doc.getLineEndOffset(endLine)

		val list 							= activeHLs[editor] ?: return
		val it 								= list.iterator()

		while (it.hasNext()) 
		{
			val h 							= it.next()

			// если хайлайт пересекается с изменённым отрезком строк — снимаем его немедленно
			if (rangesOverlap(h.startOffset, h.endOffset, lineStart, lineEnd)) 
			{
				try 
				{ 
					editor.markupModel.removeHighlighter(h) 
				} 
				catch (_: Exception) {}

				it.remove					()
			}
		}
	}

	private fun rangesOverlap(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean 
	{
		val start 							= maxOf(aStart, bStart)
		val end   							= minOf(aEnd, bEnd)

		return start < end
	}

	// Единая точка запроса перерисовки
	private fun requestRedraw(editor: Editor, undoRedo: Boolean = false) 
	{
		if (isChangesFromInvalidKey(editor))	return
		if (isChangesNotFromKeyboard(editor))	return
		
		val editorHash 						= editor.document.hashCode()

		redrawQueue.queue(object : Update("DHC-Redraw-$editorHash") 
		{
			override fun run() 
			{
				if (undoRedo)
				{
					_Highlight_Apply		(editor)
					lastHLTime[editor] 		= System.currentTimeMillis()

					return;
				}

				val flag 					= inFlightFlag.computeIfAbsent(editor) { java.util.concurrent.atomic.AtomicBoolean(false) }
				val pending 				= pendingFlag.computeIfAbsent(editor) { java.util.concurrent.atomic.AtomicBoolean(false) }

				if (!flag.compareAndSet(false, true)) 
				{
					// Уже в работе → ставим флажок "надо повторить"
					pending.set				(true)
					return
				}

				try 
				{
					_Highlight_Apply		(editor)
					lastHLTime[editor] 		= System.currentTimeMillis()
				} 
				finally 
				{
					flag.set				(false)

					if (pending.getAndSet(false)) 
					{
						// сразу перезапускаем один раз, если был пропущенный запрос
						requestRedraw		(editor)
					}
				}
 			}
		})
	}
	
	// helper: likely-from-keyboard?
	private fun isChangesFromInvalidKey(editor: Editor): Boolean 
	{
		return lastHLValidKey[editor] == false
	}
	
	// helper: likely-from-keyboard?
	private fun isChangesFromKeyboard(editor: Editor): Boolean 
	{
		val ts = lastHLUserKey[editor] ?: return false
		return (System.currentTimeMillis() - ts) <= 1000L // window configurable
	}
	
	// helper: likely-from-keyboard?
	private fun isChangesNotFromKeyboard(editor: Editor): Boolean 
	{
		return !isChangesFromKeyboard(editor)
	}

    private fun _Highlight_Apply(editor: Editor) 
    {
		LOG.info                             ("[DHC]: try highlightEditor")

		_updateInfo							= UpdateInfo(false, null)

        try 
        {
            // If a previous run's overlay exists for this editor, remove it immediately
            overlayManager.removeOverlayImmediate(editor)

            // Очищаем старые подсветки 	(на EDT)
            //this._ClearHighlighters 		(editor)

            // prepare and show overlay immediately
            overlayManager.prepareOverlay   (editor)

            val document                    = editor.document
            val settings                    = SimpleSettings.getInstance()

            // bump generation and cancel previous running futures for this editor
            val gen                         = generationMap.computeIfAbsent(editor) { AtomicInteger(0) }.incrementAndGet()

            // cancel previous running futures for this editor
            runningFutures[editor]?.forEach { 
                future -> try { future.cancel(true) } catch (_: Exception) {} 
            }

            runningFutures[editor]          = mutableListOf()

            // cancel named running tasks for this editor
            runningTasks.remove(editor)?.values?.forEach { 
                try { it.cancel(true) } catch (_: Exception) {} 
            }

            runningTasks[editor]            = ConcurrentHashMap()

            this._ClearOtherEditors         (editor)

            // (Не трогаем timestamp throttle здесь — управляется отдельным методом)

            // Launch background highlighting in a helper to keep highlightEditor concise
            AppExecutorUtil.getAppExecutorService().execute {
                try 
                {
                    this._HighlightingBackground(editor, document, settings, gen)
                } 
                catch (ex: Exception) { LOG.warn("[DHC]: highlightEditor - background highlighting", ex) }
            }

        } 
        catch (e: Exception) 
        {
            LOG.warn("[DHC]: Error scheduling highlighting", e)
        }
    }

	 // Extracted background runner so highlightEditor remains concise
    private fun _HighlightingBackground(editor: Editor, document: Document, settings: SimpleSettings, gen: Int) 
    {
		val _hash 							= try { editor.document.hashCode() } catch (_: Exception) {0}
        
 		LOG.info						   ("[DHC]: _HighlightingBackground start for editor=${_hash}, gen=$gen") 

        val startTime 						= System.currentTimeMillis()

        // Read text and line count in one read action
        val (text, lineCount) = application.runReadAction<Pair<String, Int>> {
            document.text to document.lineCount
        }

        application.invokeLater {
            try { overlayManager.prepareOverlay(editor, enabledRules.size) } catch (_: Exception) {}
        }

        val results                         = mutableListOf<Pair<TextRange, TextAttributes>>()

        // Prepare condition search window
        val conditionLinesLimit             = settings.conditionSearchLines
        val conditionWindowText: String? 	= if (conditionLinesLimit <= 0) null else substringByLines(text, conditionLinesLimit)
        val hayForConditionSearch: String 	= conditionWindowText ?: text

        // Per-rule condition (key) detection
        val ruleConditionFound 				= ConcurrentHashMap<String, Boolean>()
        val rulesWithCondition 				= enabledRules.filter { try { it.condition.trim().isNotEmpty() } catch (_: Exception) { false } }
        
        if (rulesWithCondition.isNotEmpty()) 
        {
            this._RuleKeySearch                (editor, hayForConditionSearch, rulesWithCondition, ruleConditionFound)
        }

        this._FileChunkedSearch                (editor, text, lineCount, conditionWindowText, enabledRules, ruleConditionFound, results)

        // Apply accumulated results on EDT if still current
        val currentGen                         = generationMap[editor]?.get() ?: gen

        application.invokeLater {

            try 
            {
                val latestGen 				= generationMap[editor]?.get() ?: -1

                if (latestGen != currentGen) 
				{
					return@invokeLater
				}

                val stillOpen 				= edtFactory.allEditors.any { it === editor }

                if (!stillOpen) 			
				{
					return@invokeLater
				}

                // this._ClearHighlighters  (editor)

 				val markupModel 			= editor.markupModel
				val current 				= activeHLs.getOrPut(editor) { mutableListOf<RangeHighlighter>() }

				// desired: List<Pair<TextRange, TextAttributes>>
				val desired 				= results

				// --- 1) Удаляем только устаревшие (нет в desired по range) ---
				val desiredRanges 			= desired.map { it.first }.toHashSet()
				val iter 					= current.iterator()

				while (iter.hasNext()) 
				{
					val h 					= iter.next()
					val r 					= com.intellij.openapi.util.TextRange(h.startOffset, h.endOffset)

					if (r !in desiredRanges) 
					{
						try { markupModel.removeHighlighter(h) } catch (_: Exception) {}
						iter.remove()
					}
				}

				// --- 2) Добавляем недостающие и обновляем атрибуты существующих ---
				/*
				Для быстрого поиска существующих делаем индекс по диапазону.
				Если у тебя бывают highlighters с разными типами на одном и том же диапазоне — 
				тогда нужен ruleId в userData и ключ из (range, ruleId). Если нет — достаточно range.
				*/
				val existingByRange 		= current.associateBy { com.intellij.openapi.util.TextRange(it.startOffset, it.endOffset) }

				for ((range, attributes) in desired) 
				{
					val key 				= range
					val existing 			= existingByRange[key]

					if (existing == null) 
					{
						val h 				= markupModel.addRangeHighlighter (
																			  	range.startOffset, range.endOffset,
																			  	HighlighterLayer.SELECTION - 1, attributes,
																			  	HighlighterTargetArea.EXACT_RANGE
																			  ).apply {
																			  	isGreedyToLeft 	= false
																			  	isGreedyToRight = false
																			  }
						current.add(h)
					} 
					else 
					{
						// обновление без удаления — через RangeHighlighterEx
						val ok 				= if (existing is RangeHighlighterEx) 
										      {
										      	try 
										      	{
										      		existing.setTextAttributes(attributes)
										      		true
										      	} catch (_: Exception) { false }
										      } else false

						if (!ok) 
						{
							// fallback: если тип без setTextAttributes — пере-создадим (редко, но надёжно)
							try { markupModel.removeHighlighter(existing) } catch (_: Exception) {}

							val h 			= markupModel.addRangeHighlighter(
																			 	range.startOffset, range.endOffset,
																			 	HighlighterLayer.SELECTION - 1, attributes,
																			 	HighlighterTargetArea.EXACT_RANGE
																			 ).apply {
																			 	isGreedyToLeft 	= false
																			 	isGreedyToRight = false
																			 }
							current.remove	(existing)
							current.add		(h)
						}
					}
				}

                activeHLs[editor] 			= current

                // show duration and final stats
                val durationMs 				= System.currentTimeMillis() - startTime

                try 
                {
                    try { LOG.info("[DHC]: _HighlightingBackground completed in ${durationMs}ms for editor=${editor.document.hashCode()}, gen=$gen") } catch (_: Exception) {}

                    overlayManager.setCompletedToTotal(editor)
                    overlayManager.showOrUpdateOverlay(editor, "", inProgress = false)
                } 
                catch (_: Exception) {}

                javax.swing.Timer(5000) 	{ overlayManager.hideOverlay(editor) }.apply { isRepeats = false; start() }
            } 
            catch (ex: Exception) 
			{ 
				LOG.warn					("[DHC]: _HighlightingBackground.invokeLater", ex) 
			}
        }
    }
    
    // Публичный метод для обновления подсветки во всех редакторах
    fun refreshAllHighlighting() 
    {
        application.invokeLater { edtFactory.allEditors.filter { it.project == project }.forEach     { editor -> _Highlight_Apply(editor) } }
    } 

    // Cancel all running tasks and remove overlays for a specific editor
    fun cancelTasksForEditor(editor: Editor) 
    {
        try 
        {
            runningFutures.remove(editor)?.forEach { 
                f -> try { f.cancel(true) } catch (ex: Exception) { LOG.warn("[DHC]: cancelTasksForEditor - cancel future", ex) } 
            }

            runningTasks.remove(editor)?.values?.forEach { 
                f -> try { f.cancel(true) } catch (ex: Exception) { LOG.warn("[DHC]: cancelTasksForEditor - cancel named task", ex) } 
            }

            // remove overlays immediately
           application.invokeLater {
                try { overlayManager.removeOverlayImmediate(editor) } catch (ex: Exception) { LOG.warn("[DHC]: cancelTasksForEditor -> overlayManager.removeOverlayImmediate", ex) }
                try { _ClearHighlighters(editor) } catch (ex: Exception) { LOG.warn("[DHC]: cancelTasksForEditor -> _ClearHighlighters", ex) }
            }
        } 
        catch (ex: Exception) { LOG.warn("[DHC]: cancelTasksForEditor", ex) }
    }

    // Cancel a named task for an editor (if present) and hide its overlay
    fun cancelTaskById(editor: Editor, taskId: String) 
    {
        try 
        {
            val map             = runningTasks[editor] ?: return
            val fut             = map.remove(taskId)

            try { fut?.cancel(true) } catch (ex: Exception) { LOG.warn("[DHC]: cancelTaskById - cancel future", ex) }

            // hide overlay for that task
           application.invokeLater {
                try { overlayManager.hideTaskOverlay(editor, taskId) } catch (ex: Exception) { LOG.warn("[DHC]: cancelTaskById -> overlayManager.hideTaskOverlay", ex) }
            }
        } 
        catch (ex: Exception) { LOG.warn("[DHC]: cancelTaskById", ex) }
    }

    // Cancel everything across all editors
    fun cancelAllTasks() 
    {
        try 
        {
            val editors         = runningTasks.keys.toList()

            for (e in editors) 
            {
                cancelTasksForEditor(e)
            }

            // also clear global overlays
           application.invokeLater {
                try 
				{ 
					removeAllOverlaysExcept(null) 
				} 
				catch (ex: Exception) 
				{ 
					LOG.warn   ("[DHC]: cancelAllTasks -> removeAllOverlaysExcept", ex) 
				}
           }
        } 
        catch (ex: Exception) { LOG.warn("[DHC]: cancelAllTasks", ex) }
    }

    override fun dispose() 
    {
   		lastHLValidKey 			.clear()
   		lastHLTime 				.clear()

   		lastHLDocChange 		.clear()

		inFlightFlag 			.clear()
		pendingFlag 			.clear()

   		generationMap 			.clear()
   		runningFutures 			.clear()
   		activeHLs 				.clear()
   		runningTasks 			.clear()
    }

    companion object {
        fun getInstance(project: Project): DirectHighlighterComponent = project.getService(DirectHighlighterComponent::class.java)
    }

    // Remove overlays/highlighters/timers for all editors except the provided one
    private fun removeAllOverlaysExcept(keep: Editor?) 
    {
        try 
        {
            val editors                     = edtFactory.allEditors.filter { it.project == project }

            for (_editor in editors) 
            {
                if (keep != null && _editor === keep) 
                {
                    continue
                }

                try 
                {
                    overlayManager.removeOverlayImmediate(_editor)
                    overlayManager.removeAllForEditor(_editor)

                    this._ClearHighlighters(_editor)

                    runningFutures.remove(_editor)?.forEach { 
                        f -> try { f.cancel(true) } catch (ex: Exception) { LOG.warn("[DHC]: removeAllOverlaysExcept - cancel future", ex) } 
                    }

                    runningTasks.remove(_editor)?.values?.forEach { 
                        f -> try { f.cancel(true) } catch (ex: Exception) { LOG.warn("[DHC]: removeAllOverlaysExcept - cancel named task", ex) } 
                    }
                } 
                catch (ex: Exception) 
				{ 
					LOG.warn   ("[DHC]: removeAllOverlaysExcept - per-editor cleanup", ex) 
				}
            }
        } 
        catch (ex: Exception) 
		{ 
			LOG.warn           ("[DHC]: removeAllOverlaysExcept", ex) 
		}
    }

    private fun _RemovePanel(panel: JPanel?, editor: Editor?)
    {
        try
        {
            if (panel == null)              return
            if (editor == null)             return

            val rootPane                    = javax.swing.SwingUtilities.getRootPane(editor.contentComponent)
            val layered                     = rootPane?.layeredPane

            if (layered != null && panel.parent === layered) 
            {
                layered.remove              (panel)
                layered.repaint             ()
            } 
            else 
            {
                try     { editor.contentComponent.remove(panel) } 
                catch   (ex: Exception) { LOG.warn("[DHC]: _RemovePanel - remove from contentComponent", ex) }

                try     { editor.contentComponent.revalidate(); editor.contentComponent.repaint() } 
                catch   (ex: Exception) { LOG.warn("[DHC]: _RemovePanel - revalidate/repaint", ex) }
            }
        } 
        catch (ex: Exception) { LOG.warn("[DHC]: _RemovePanel", ex) }
    }
    
    private fun _ClearHighlighters(editor: Editor) 
    {
        activeHLs[editor]?.forEach { 
            highlighter -> try { editor.markupModel.removeHighlighter(highlighter) } catch (ex: Exception) { LOG.warn("[DHC]: _ClearHighlighters - removeHighlighter", ex) }
        }

        activeHLs.remove            (editor)
    }

    private fun _ClearOtherEditors(editor: Editor) 
    {
        // Also cancel running tasks for other editors and clean up their overlays/highlighters
        try 
        {
            val others                      = runningFutures.keys.toList()

            for (other in others) 
            {
                if (other === editor)       continue

                try 
                {
                    runningFutures[other]?.forEach { f -> try { f.cancel(true) } catch (_: Exception) {} }

                    runningFutures.remove(other)

                    // bump generation for other to mark results stale
                    generationMap.computeIfAbsent(other) { AtomicInteger(0) }.incrementAndGet()

                    // remove overlay and clear visual highlighters for other editor
                    try { overlayManager.removeOverlayImmediate(other) } catch (_: Exception) {}
                    try { this._ClearHighlighters(other)     } catch (_: Exception) {}
                } 
                catch (ex: Exception) { LOG.warn("[DHC]: _ClearOtherEditors - per-editor cleanup", ex) }
            }
        } 
        catch (ex: Exception) { LOG.warn("[DHC]: _ClearOtherEditors", ex) }
    }
    
    private fun createTextAttributes(rule: HighlightRule): TextAttributes 
    {
        return TextAttributes().apply {
            // Обрабатываем цвет фона
            if (rule.backgroundColor.isNotEmpty()) 
            {
                try 
                {
                    backgroundColor         = parseColor(rule.backgroundColor)
                } 
                catch (e: Exception) { backgroundColor = null }
            }
            
            // Обрабатываем цвет текста
            if (rule.foregroundColor.isNotEmpty()) 
            {
                try 
                {
                    foregroundColor         = parseColor(rule.foregroundColor)
                } 
                catch (e: Exception) { foregroundColor = null }
            }
            
            // Применяем стиль шрифта
            fontType                         = rule.fontStyle.value
            
            // Применяем оформление текста
            when (rule.textDecoration) 
            {
                HighlightRule.TextDecoration.UNDERLINE -> 
                {
                    effectType 				= com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE
                    effectColor 			= foregroundColor ?: java.awt.Color.BLACK
                }

                HighlightRule.TextDecoration.STRIKETHROUGH -> 
                {
                    effectType 				= com.intellij.openapi.editor.markup.EffectType.STRIKEOUT
                    effectColor 			= foregroundColor ?: java.awt.Color.BLACK
                }

                HighlightRule.TextDecoration.NONE -> 
                {
                    // Никакого эффекта
                }
            }
        }
    }
    
    private fun parseColor(colorString: String): java.awt.Color? 
    {
        return when 
        {
            colorString.isEmpty () 				-> null
            colorString.startsWith ("rgba(") 	-> parseRgbaColor        (colorString)
            colorString.startsWith ("#") 		-> java.awt.Color.decode(colorString)

            else -> java.awt.Color.decode("#$colorString")
        }
    }
    
    private fun parseRgbaColor(rgba: String): java.awt.Color? 
    {
        try 
        {
            // rgba(255, 107, 53, 0.1) -> [255, 107, 53, 0.1]
            val values 						= rgba.removePrefix        	("rgba(")
 											.removeSuffix       		(")")
 											.split            			(",")
 											.map                 		{it.trim()}

            if (values.size >= 3) 
            {
                val r 						= values[0].toInt()
                val g 						= values[1].toInt()
                val b 						= values[2].toInt()
                val a 						= if (values.size > 3) (values[3].toFloat() * 255).toInt() else 255

                return java.awt.Color        (r, g, b, a)
            }
        } 
        catch (e: Exception) 
        {
            LOG.warn("[DHC]: parseRgbaColor failed for '$rgba'", e)
        }

        return null
    }
    
    private fun findAllMatches(text: String, target: String): List<TextRange> 
    {
        val matches                         = mutableListOf<TextRange>()
        var index                             = 0

        while (true) 
        {
            index                             = text.indexOf        (target, index)

            if (index == -1) break

            matches.add                        (TextRange(index, index + target.length))

            index                             += target.length
        }

        return matches
    }
    
    private fun findCommentMatches(text: String, commentPattern: String): List<TextRange> 
    {
        val matches                         = mutableListOf<TextRange>()

        try 
        {
            // Создаем расширенный паттерн для поиска с отступами
            val searchPattern                 = ".*$commentPattern"
            val searchRegex                 = Regex(searchPattern)
            val commentRegex                 = Regex(commentPattern)
            
            val searchResults                 = searchRegex.findAll        (text)
            
            for (searchResult in searchResults) 
            {
                val fullMatch                 = text.substring            (searchResult.range.first, searchResult.range.last + 1)
                val commentMatch             = commentRegex.find            (fullMatch)

                if (commentMatch != null) 
                {
                    val commentStart         = searchResult.range.first + commentMatch.range.first
                    val commentEnd             = searchResult.range.first + commentMatch.range.last + 1
                    
                    matches.add                (TextRange(commentStart, commentEnd))
                }
            }
        } 
        catch (e: Exception)     { LOG.warn("[DHC]: findCommentMatches(text, pattern)", e) }

        return matches
    }

    // Overload: accept precompiled Regex for comment matching
    private fun findCommentMatches(text: String, commentRegex: Regex): List<TextRange> 
    {
        val matches                         = mutableListOf<TextRange>()

        try 
        {
            val searchResults                 = commentRegex.findAll(text)

            for (res in searchResults) 
            {
                matches.add                    (TextRange(res.range.first, res.range.last + 1))
            }
        }
        catch (e: Exception) { LOG.warn("[DHC]: findCommentMatches(text, regex)", e) }

        return matches
    }

    private fun findRegexMatches(text: String, regexPattern: String): List<TextRange> 
    {
        val matches                         = mutableListOf<TextRange>()

        try 
        {
            val regex 						= Regex                (regexPattern)
            val matchResults 				= regex.findAll        (text)
            
            for (matchResult in matchResults) 
            {
                matches.add 				(TextRange(matchResult.range.first, matchResult.range.last + 1))
            }
        } 
        catch (e: Exception) 	{ LOG.warn("[DHC]: findRegexMatches(pattern)", e) }

        return matches
    }

    // Overload: accept precompiled Regex to avoid compiling repeatedly
    private fun findRegexMatches(text: String, regex: Regex): List<TextRange> 
    {
        val matches                         = mutableListOf<TextRange>()

        try 
        {
            val matchResults                 = regex.findAll(text)

            for (matchResult in matchResults) 
            {
                matches.add                    (TextRange(matchResult.range.first, matchResult.range.last + 1))
            }
        } 
        catch (e: Exception) { LOG.warn("[DHC]: findRegexMatches(regex)", e) }

        return matches
    }

    // Return substring consisting of first N lines (or whole text if N >= line count)
    private fun substringByLines(text: String, linesLimit: Int): String 
    {
        if (linesLimit <= 0)                 return text
            
        var linesTaken 						= 0
        var pos 							= 0
        val len 							= text.length

        while (pos < len && linesTaken < linesLimit) 
        {
            val next 						= text.indexOf('\n', pos)

            if (next == -1) 
            {
                return text.substring(0, len)
            } 
            else 
            {
                pos                         = next + 1

                linesTaken++
            }
        }

        return if (pos >= len) text else text.substring(0, pos)
    }

    // Search per-rule condition/key in the provided haystack and update ruleConditionFound map
    private fun _RuleKeySearch(editor: Editor, hay: String, rulesWithCondition: List<HighlightRule>, ruleConditionFound: ConcurrentHashMap<String, Boolean>) 
    {
        try 
        {
            val keyExecutor 				= AppExecutorUtil.getAppExecutorService()
            val keyCompletion 				= java.util.concurrent.ExecutorCompletionService<Pair<HighlightRule, Boolean>>(keyExecutor)
            val keyFutures 					= mutableListOf<java.util.concurrent.Future<*>>()

            for (rule in rulesWithCondition) 
            {
                val rid 					= if (rule.id.isNotEmpty()) rule.id else java.util.UUID.randomUUID().toString()
                val callable 				= java.util.concurrent.Callable<Pair<HighlightRule, Boolean>> 
                                            {
                                                val found = try { hay.contains(rule.condition.trim()) } catch (_: Exception) { false }
                                                rule to found
                                            }

                keyFutures.add                (keyCompletion.submit(callable))

                // show per-key overlay
                try 
                {
                    application.invokeLater {
                        try 
                        {
                            val tid         = "key_${rid}"

                            overlayManager.showOrUpdateTaskOverlay(editor, tid, "Searching key for '${rule.name.ifEmpty { rule.targetWord }}'")
                        } 
                        catch (ex: Exception) 
						{ 
							LOG.warn		("[DHC]: _RuleKeySearch - showOrUpdateTaskOverlay", ex) 
						}
                    }
                } 
                catch (ex: Exception) 
				{ 
					LOG.warn				("[DHC]: _RuleKeySearch - submit callable", ex) 
				}
            }

            var keyProcessed 				= 0

            while (keyProcessed < keyFutures.size) 
            {
                try 
                {
                    val completed 			= keyCompletion.take()
                    val (rule, found) 		= try { completed.get() } catch (_: Exception) { null to false }

                    if (rule != null) 
                    {
                        val rid 			= if (rule.id.isNotEmpty()) rule.id else java.util.UUID.randomUUID().toString()

                        ruleConditionFound[rid] = found

                        application.invokeLater {
                            try 
                            {
                                val tid     = "key_${rid}"

                                if (!found) 
                                {
                                    overlayManager.showOrUpdateTaskOverlay(editor, tid, "Key not found for '${rule.name.ifEmpty { rule.targetWord }}' — skipping")
                                    javax.swing.Timer(1500) { try { overlayManager.hideTaskOverlay(editor, tid) } catch (_: Exception) {} }.apply { isRepeats = false; start() }
                                } 
                                else 
                                {
                                    try { overlayManager.hideTaskOverlay(editor, tid) } catch (_: Exception) {}
                                }
                            } 
                            catch (ex: Exception) 
							{ 
								LOG.warn	("[DHC]: _RuleKeySearch - invokeLater", ex) 
							}
                        }
                    }
                } 
                catch (ex: Exception) 
                {} 
                finally 
                {
                    keyProcessed++
                }
            }
        } 
        catch (ex: Exception) 
		{ 
			LOG.warn						("[DHC]: _RuleKeySearch", ex) 
		}
    }

    // Large-file chunked processing
    private fun _FileChunkedSearch
	(
		editor					: Editor, 
		text					: String,  
		lineCount				: Int, 
		conditionWindowText		: String?, 
		enabledRules			: List<HighlightRule>, 
		ruleConditionFound		: ConcurrentHashMap<String, Boolean>, 
		results					: MutableList<Pair<TextRange, TextAttributes>>
	) 
    {
        try 
        {
            val chunkSize  					= 100

            val lineStarts 					= application.runReadAction<IntArray> 
                                            {
                                                val arr 				= IntArray(lineCount)
                                                var pos 				= 0
                                                var line 				= 0
                                                val len 				= text.length

                                                while (line < lineCount && pos <= len) 
                                                {
                                                    arr[line] 			= pos

                                                    val next 			= text.indexOf('\n', pos)

                                                    if (next == -1) break else { pos = next + 1; line++ }
                                                }
                                                arr
                                            }

            val visibleRange                 = try 
                                            {
                                                val rect 				= editor.scrollingModel.visibleArea
                                                val topLP 				= editor.xyToLogicalPosition(java.awt.Point(0, rect.y))
                                                val bottomLP 			= editor.xyToLogicalPosition(java.awt.Point(0, rect.y + rect.height - 1))
                                                val topLine 			= topLP.line.coerceAtLeast(0)
                                                val bottomLine 			= bottomLP.line.coerceAtMost(lineCount - 1)

                                                topLine..bottomLine
                                            } 
                                            catch (e: Exception) { 0..0 }

            val blocks 						= mutableListOf<Block>()
            var s 							= 0

            while (s < lineCount) 
            {
                val e 						= kotlin.math.min(s + chunkSize - 1, lineCount - 1)
                val startOffset 			= lineStarts.getOrNull(s) ?: 0
                val endOffset 				= if (e + 1 < lineStarts.size) lineStarts[e + 1] else text.length
                var pr 						= 0

                if ((s..e).any { it in visibleRange }) pr += 1000

                blocks.add                    (Block(s, e, startOffset, endOffset, pr))

                s                             = e + 1
            }

            blocks.sortByDescending         { it.priority }

            val executor                    = AppExecutorUtil.getAppExecutorService()
            val completionService           = java.util.concurrent.ExecutorCompletionService<List<Pair<TextRange, TextAttributes>>>(executor)
            val futureToBlock               = ConcurrentHashMap<java.util.concurrent.Future<*>, Block>()
            val submitted                   = mutableListOf<java.util.concurrent.Future<*>>()

            for ((bi, block) in blocks.withIndex()) 
            {
                val tid                     = "block_${bi}"

                block.id = tid
                try 
                {
                    application.invokeLater {
                        try 
                        {
                            overlayManager.showOrUpdateTaskOverlay(editor, tid, "Processing lines ${block.startLine}-${block.endLine}")
                        } 
                        catch (ex: Exception) { LOG.warn("[DHC]: _FileChunkedSearch - showOrUpdateTaskOverlay", ex) }
                    }
                } 
                catch (ex: Exception) { LOG.warn("[DHC]: _FileChunkedSearch - invokeLater", ex) }

                val callable 	= java.util.concurrent.Callable<List<Pair<TextRange, TextAttributes>>> 
                {
                    val localResults 		= mutableListOf<Pair<TextRange, TextAttributes>>()
                    val blockText 			= try { text.substring(block.startOffset, block.endOffset) } catch (_: Exception) { "" }

                    for (rule in enabledRules) 
                    {
                        val rid 			= if (rule.id.isNotEmpty()) rule.id else java.util.UUID.randomUUID().toString()
                        val hasCondition 	= try { rule.condition.trim().isNotEmpty() } catch (_: Exception) { false }

                        if (hasCondition) 
                        {
                            val found 		= ruleConditionFound[rid] ?: false

                            if (!found)     continue
                        }

                        val shouldHighlight = if (rule.condition.trim().isEmpty()) { true } else 
                                            {
                                                val hay = conditionWindowText ?: text

                                                hay.contains            (rule.condition.trim())
                                            }

                        if (!shouldHighlight) 
                        {
                            continue
                        }

                        val matches         = if (rule.exclusion.trim().isEmpty()) 
                                            {
                                                this._GetTextList        (rule, blockText)
                                            } 
                                            else 
                                            {
                                                val allMatches 			= this._GetTextList(rule, blockText)

                                                this._FilterMatches 	(blockText, allMatches, rule.exclusion.trim())
                                            }

                        val attributes 		= createTextAttributes	(rule)

                        for (r in matches) 
                        {
                            val gStart 		= block.startOffset + r.startOffset
                            val gEnd 		= block.startOffset + r.endOffset

                            localResults.add(TextRange(gStart, gEnd) to attributes)
                        }
                    }
                    localResults
                }

                val future                     = completionService.submit(callable)

                submitted.add                (future)

                futureToBlock[future]         = block

                runningFutures.computeIfAbsent(editor)     { mutableListOf() }.add(future)
                runningTasks.computeIfAbsent(editor)     { ConcurrentHashMap() }[tid] = future
            }

            this._ProcessCompletedFutures    (editor, submitted, completionService, futureToBlock, blocks.size, results)
        } 
        catch (ex: Exception) { LOG.warn("[DHC]: _FileChunkedSearch", ex) }
    }

     // helper: process completionService results and update overlays/results on EDT
    private fun _ProcessCompletedFutures(
        editor                : Editor, submitted: List<java.util.concurrent.Future<*>>, 
        completionService     : java.util.concurrent.ExecutorCompletionService<List<Pair<TextRange, TextAttributes>>>, 
        futureToBlock         : ConcurrentHashMap<java.util.concurrent.Future<*>, Block>,
        blocksSize            : Int, 
        results               : MutableList<Pair<TextRange, TextAttributes>>) 
    {
        for (idx in 0 until submitted.size) 
        {
            try 
            {
                val completed               = completionService.take    ()
                val block                   = futureToBlock.remove      (completed)
                val res                     = try { completed.get() } catch (_: Exception) { null }

                this._RemoveComplitedAtList    (editor, completed)

                application.invokeLater {
                    try 
                    {
                        if (res != null && res.isNotEmpty()) 
                        {
                            results.addAll(res)

                            if (SimpleSettings.getInstance().applyPartialOnRuleComplete) 
                            {
                                this._ApplyPartialResults(editor, res)
                            }
                        }

                        overlayManager.incrementCompleted              (editor)
                        overlayManager.setTotalRules                   (editor, blocksSize)
                        overlayManager.showOrUpdateOverlay             (editor, "", inProgress = true)

                        try 
                        {
                            val bid     = block?.id ?: "block_${idx}"

                            overlayManager.hideTaskOverlay(editor, bid)
                        } 
                        catch (_: Exception) {}
                    } 
                    catch (ex: Exception) { LOG.warn("[DHC]: _ProcessCompletedFutures - invokeLater", ex) }
                }
            } 
            catch (ex: InterruptedException) 
            {
                Thread.currentThread().interrupt()
                LOG.warn("Interrupted while waiting for completion", ex)
                break
            } 
            catch (ex: Exception) 
            {
                LOG.warn("Error while processing completed future", ex)
            }
        }
    }

    // helper: remove completed future instances from runningTasks[editor]?.values
    private fun _RemoveComplitedAtList(editor: Editor, completed: java.util.concurrent.Future<*>) 
    {
        try 
        {
            runningTasks[editor]?.values?.let { 
                
                values ->
                {
                    val toRemove = values.filter { it == completed }

                    for (item in toRemove) 
                    {
                        try { values.remove(item) } catch (_: Exception) {}
                    }
                }
            }
        } 
        catch (_: Exception) {}

        try 
        { 
            runningFutures[editor]?.remove    (completed) 
        } 
        catch (_: Exception) {}
    }

    private fun _GetTextList(rule: HighlightRule, blockText: String) : List<TextRange>
    {
        return     if (rule.isRegex) 
                {
                    if (rule.targetWord.startsWith("//")) 
                    {
                        val regex             = try { Regex(rule.targetWord) } catch (_: Exception) { null }

                        if (regex != null)     findCommentMatches(blockText, regex) else emptyList()
                    } 
                    else 
                    {
                        val preset             = try { Regex(rule.targetWord) } catch (_: Exception) { null }

                        if (preset != null) findRegexMatches(blockText, preset) else emptyList()
                    }
                } 
                else 
                {
                    findAllMatches            (blockText, rule.targetWord)
                }
    }
    
    private fun _FilterMatches(text: String, matches: List<TextRange>, exclusion: String): List<TextRange> 
    {
        val filteredMatches                 = mutableListOf<TextRange>()
        
        for (match in matches) 
        {
            // Получаем строку, содержащую найденное совпадение
            val lines 						= text.lines()
            var currentPos 					= 0
            var matchLine 					= ""
            
            // Находим строку с совпадением
            for (line in lines) 
            {
                val lineEnd                 = currentPos + line.length

                if (match.startOffset >= currentPos && match.startOffset < lineEnd) 
                {
                    matchLine 				= line
                    break
                }

                currentPos 					= lineEnd + 1 // +1 для символа новой строки
            }
            
            // Проверяем, содержит ли строка исключение
            if (!matchLine.contains(exclusion)) 
            {
                filteredMatches.add(match)
            } 
            else 
            {
                // println("[DirectHighlighter] Excluded match at ${match.startOffset} because line contains '$exclusion'")
            }
        }
        
        return filteredMatches
    }
    
    // Apply a set of partial results immediately (EDT)
    private fun _ApplyPartialResults(editor: Editor, partial: List<Pair<TextRange, TextAttributes>>) 
    {
        try 
        {
            if (partial.isEmpty()) 
            {
                return
            }

            val markupModel                 = editor.markupModel
            val highlighters                = activeHLs.computeIfAbsent(editor) { mutableListOf() }

            for ((range, attributes) in partial) 
            {
                try 
                {
                    val highlighter         = markupModel.addRangeHighlighter (
                                                                                range.startOffset,
                                                                                range.endOffset,
                                                                                HighlighterLayer.SELECTION - 1,
                                                                                attributes,
                                                                                com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE
                                                                              )

                    highlighters.add        (highlighter)
                } 
                catch (ex: Exception) { LOG.warn("[DHC]: _ApplyPartialResults - addRangeHighlighter", ex) }
            }

            activeHLs[editor]         = highlighters
        } 
        catch (ex: Exception) { LOG.warn("[DHC]: _ApplyPartialResults", ex) }
    }
}
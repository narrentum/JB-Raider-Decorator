# JetBrains Rider/IntelliJ Code Highlighter Plugin

Плагин для подсветки специальных слов и паттернов в коде в JetBrains Rider и IntelliJ IDEA.

## Возможности

- 🎨 Подсветка ключевых слов: `_this`, `TODO`, `console.log`, `React` компонентов
- ⚙️ Настраиваемые правила подсветки через UI
- 🚀 Мгновенное применение изменений настроек
- 🎯 Поддержка регулярных выражений
- 🌈 Настройка цветов текста и фона
- ✨ Стили шрифта и декорации текста

## Поддерживаемые IDE

- JetBrains Rider 2024.3+
- IntelliJ IDEA 2024.3+
- Совместим до версии 2025.2.x

## Установка

1. Скачайте последнюю версию из [Releases](https://github.com/narrentum/JB-Raider-Decorator/releases)
2. В IDE: `File → Settings → Plugins`
3. Нажмите ⚙️ → `Install Plugin from Disk...`
4. Выберите скачанный ZIP файл
5. Перезапустите IDE

## Настройка

1. Откройте `File → Settings → Tools → Simple Code Highlighter`
2. Или используйте `Tools → _ Mega → Highlighting Rules Manager`
3. Настройте правила подсветки по своему вкусу

## Сборка из исходников

```bash
git clone https://github.com/narrentum/JB-Raider-Decorator.git
cd JB-Raider-Decorator
./gradlew buildPlugin -x buildSearchableOptions
```

Готовый плагин будет в папке `build/distributions/`

## Разработка

- **Kotlin** 2.2.0
- **JDK** 17
- **IntelliJ Platform SDK** 2024.3
- **Gradle** 8.8

## Версия

**1.2.4** - Исправлен UI настроек с упрощенной таблицей правил

## Лицензия

MIT License

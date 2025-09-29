# CLAUDE_StyleRules.md

These rules define formatting, naming, and structure requirements for Claude Code to match the user's coding style.

## Code Formatting Rules

1. Indentation and Alignment
	Use tab indentation (never spaces) for all indentation and column alignment.
	All similar lines (assignments, function calls, variable declarations) must be vertically aligned in columns relative to each other across the entire file, not just within individual {} blocks.
	Align = signs vertically within groups of related fields, properties, and local variables.
	Align method parameters in multiple lines if length exceeds 80 characters.

2. Logical Block Separation
	Insert a blank line between logically separate code blocks within methods.
	If a block of function calls is followed by a block of assignments, insert a single blank line between these blocks.
	Always insert a blank line between a group of variable declarations and the subsequent actions (calls, assignments, etc.).
	Insert a blank line before a return, break, or continue statement if it follows any other operation.
	Extra blank lines should be used between logically distinct code sections inside methods.

3. Braces and Structure
	Opening curly brace { must always be on a new line.
	Closing curly brace } must always be on a new line after the block content.
	Organize class members using #region ... only for large logical clusters (e.g., Init, Update, Destroy).
	Generated classes must include at least one logic-decomposed #region block if complexity > 100 LOC.

4. Naming Conventions
	PascalCase for types, methods, and properties.
	camelCase for fields, variables, and parameters.
	Use _camelCase for all private fields, including serialized ones.
	Boolean fields and properties must start with is, has, can, etc.
	Use one-letter aliases (R, UR, v3, q4) consistently where semantically meaningful.
	Avoid vague names like temp, data, manager.
	Delegate fields should use On, Start, Refresh, Reset, etc. prefixes.

5. Attributes and Modifiers
	Place [SerializeField], [ContextMenu], and other attributes on a separate line directly above the declaration, with no blank lines between.

6. Expression-bodied Members
	Use compact expression-bodied properties (=>) for getters and delegates.
	Use expression-bodied members (=>) only for short getters/setters.
	For longer methods, always use curly braces and line breaks.

7. Method and Member Ordering
	Method ordering: Start with lifecycle methods (Awake, Update), then methods called from Update, listed in order of invocation.
	Place helper methods immediately after the method that invokes them.
	Editor-related methods (e.g., inspector extensions, gizmos) must be placed last in the file.
	Prefix private methods with _, especially if they are extensions or helpers (e.g., _DrawPlaneGizmo).
	Methods that extend Unity behavior but are not standard Unity methods should start with _ to indicate private scope.

8. Comments
	Always place summary comments (/// <summary>) for public and private members.
	Comments must be aligned to the code column they refer to.
	For multi-line comments, each new comment line must start on a new line.

9. Miscellaneous
	Prefer declaring var for inferred types when it improves readability.
	Cache Transform references in dedicated fields (e.g., _formA, _formB) instead of repeated .transform access.
	Do not merge validation and initialization into a single method unless explicitly needed.
	Avoid inverted checks like if (!ValidateObjects()) — prefer direct logic (if (IsNotValid())) to reduce cognitive load.

10. Example

	if (win.graph == targetGraph)
	{
		win						.Focus						();

		_Window.current			= win;

		return win;
	}

## Naming Conventions

- Use `camelCase` for read-only properties that expose a private field without transformation  
  Example: `public float moveSpeed => _moveSpeed;`
- Use `PascalCase` for calculated or logic-based properties (e.g., involving conditions, methods, or arithmetic)  
  Example: `public float MaxSpeed => baseSpeed * speedMultiplier;`

- PascalCase for types, methods, and properties
- camelCase for fields, variables, and parameters
- Boolean fields and properties must start with `is`, `has`, `can`, etc.
- Avoid vague names like `temp`, `data`, `manager`
- Delegate fields should use `On`, `Start`, `Refresh`, `Reset`, etc. prefixes
- Use `m.Void`, `m.Void<T>` delegate-style definitions — prefer delegates over UnityEvents

## Output Formatting Rules

- Claude must generate code with aligned `=` signs and consistent spacing
- Include `#region` only for large clusters like Init, Update, Destroy
- Always place summary comments (`/// <summary>`) for public members
- Generated classes must include at least one logic-decomposed `#region` block if complexity > 100 LOC
- Expression-bodied properties and inline methods (`=>`) are preferred for accessors and short logic

## Refactored Formatting Rules (Updated)

- Align `=` for field and local variable declarations into columns
- Align method parameters in multiple lines if length exceeds 80 characters
- Use `_camelCase` for all private fields, including serialized ones
- Cache `Transform` references in dedicated fields (e.g., `_formA`, `_formB`) instead of repeated `.transform` access
- Insert extra blank lines between logically distinct code sections inside methods
- Prefix **private** methods with `_`, especially if they are extensions or helpers (e.g., `_DrawPlaneGizmo`)
- Methods that extend Unity behavior but are not standard Unity methods should start with `_` to indicate private scope
- Use `#region ...:` syntax for large logical clusters only
- Avoid inverted checks like `if (!ValidateObjects())` — prefer direct logic (`if (IsNotValid())`) to reduce cognitive load
- Always include `/// <summary>` comments for public and private methods
- Method ordering: Start with lifecycle methods (`Awake`, `Update`), then methods called from `Update`, listed in order of invocation
  - Place helper methods **immediately after** the method that invokes them
  - Editor-related methods (e.g., inspector extensions, gizmos) must be placed last in the file
- Prefer declaring `var` for inferred types when it improves readability
- Do not merge validation and initialization into a single method unless explicitly needed
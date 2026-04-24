## Context Augmentation
Your SonarQube organization has Context Augmentation enabled. Use these
tools to obtain precise, structured answers about the project instead
of file reading or text search.

### Guidelines and dependencies
- `get_guidelines`: MUST call before writing or editing source code.
  Use `mode="combined"` with `categories` (e.g. "Complexity &
  Maintainability", "Naming & Code Style"), `languages`, and `file_paths`.
- `check_dependency`: MUST call before adding or upgrading any dependency
  in a manifest or lockfile.

### Architecture (Java, JavaScript, TypeScript, Python, C#)
MUST use these when reasoning about module boundaries or dependencies;
do not infer structure from build files or imports.
- `get_current_architecture`: inspect the module or package dependency
  graph at a chosen depth.
- `get_intended_architecture`: inspect allowed/forbidden module couplings.
  MUST call before introducing or changing a cross-module dependency.

### Code navigation (Java only)
Whenever you would grep, search, or read a Java file to find, read, or
trace code, MUST use these tools instead.
- `search_by_signature_patterns`: find declarations by name, modifier,
  or annotation.
- `search_by_body_patterns`: find usages by regex in method bodies.
- `get_source_code`: read the signature and body of a specific FQN.
- `get_upstream_call_flow`: find callers of a method (e.g. refactor
  impact, entry-point auditing, trigger tracing).
- `get_downstream_call_flow`: find callees of a method (e.g. execution
  flow, downstream impact).
- `get_references`: list inbound and outbound references of a class,
  interface, or module (not methods). Use for class-level coupling.
- `get_type_hierarchy`: walk the inheritance tree of classes,
  interfaces, enums, or records.

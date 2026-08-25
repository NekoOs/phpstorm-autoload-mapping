# Legacy Autoload Mapping for PhpStorm

Legacy Autoload Mapping validates deterministic relationships between PHP declarations that already
exist and files PhpStorm already recognizes as PHP. It is intended for applications that predate
modern Composer/PSR-4 layouts. The plugin does not discover PHP in unknown file types, invent class
declarations, change file-type associations, or infer undeclared framework conventions.

PSR-0 is one bundled rule pack, not the plugin's data model.

## Rules and evaluation

Every project rule is flat and contains:

- a base directory relative to the project directory;
- a class pattern;
- a file pattern;
- optional explicit fallback `groupHash`/order metadata;
- a policy controlling named alternatives for ambiguous quick fixes.

Independent rules over the same base directory are alternatives (`OR`). Base directories are checked
from the most specific to their parents. Fallback chains are explicit; no relationship is inferred from directory
nesting or a framework name. Every complete valid result is retained as an alternative.

Patterns contain variables, transformations, and literal prefixes, suffixes, directories, and file
extensions:

```text
Class pattern: My{name:pascal}Controller
File pattern:  controllers/my-{name:kebab}.controller.php
```

```text
MyOrderController ↔ controllers/my-order.controller.php
```

Literals express known structure directly:

```text
WP_{name:pascal} ↔ class-wp-{name:kebab}.php
My{name:pascal}  ↔ my{name:lower}.php
```

No acronym expansion, pluralization, optional directory, or word splitting from an unstructured
lowercase value is inferred. For example, `{name:lower}` captures `myclass` atomically. If `My` is a
known prefix, write it as a literal as shown above.

## Canonical pattern DSL

Case formats:

| Format | Rendered example |
| --- | --- |
| `raw` (also the default) | `User` |
| `lower` | `user` |
| `upper` | `USER` |
| `pascal` | `OrderItem` |
| `camel` | `orderItem` |
| `snake` | `order_item` |
| `kebab` | `order-item` |
| `dot` | `order.item` |

Structural separators are independent stages:

| Stage | Meaning |
| --- | --- |
| `sep(_)` | underscore-separated structural components |
| `sep(/)` | path components |
| `sep(\)` | PHP namespace components |

Controlled restrictions are `length(n)` and `nonempty`. Arbitrary regular expressions and callback
transformations are intentionally unsupported.

Stages are written with `|`, applied from left to right, and inverted from right to left:

```text
Class pattern: {value:pascal|sep(_)}
File pattern:  {value:lower|sep(/)}.php

App_Dao_User ↔ app/dao/user.php
```

The captured value preserves both structural components and word boundaries. Case conversion never
has to pretend that a path is a flat string. Case normalization has a deterministic canonical
inverse and does not make a rule forward-only:

```text
{name:pascal} ↔ {name:lower}.php   # Database ↔ database.php
{name:upper}  ↔ {name:lower}.php   # DATABASE ↔ database.php
```

Adjacent variable-length captures such as `{vendor}{name}` are extraction ambiguities. Declare a
literal boundary or a controlled length when one exists:

```text
{vendor:upper|length(2)}{name:pascal}
```

Removed aliases such as `underscorePath`, `lowerPath`, `pascalUnderscore`, `_`, `/`, and `path` are
not accepted. Their behavior is expressed by composing case and `sep(...)` explicitly.

## Directional capabilities

The editor reports three independent abilities for every rule:

1. validate an existing declaration/file pair;
2. derive a file from an existing class declaration;
3. derive the expected name for an existing declaration from its file.

Missing variables limit only the affected direction. They do not invalidate the rule:

```text
{module:pascal}_{name:pascal} ↔ {name:lower}.php
```

is shown as **Solo clase → archivo**, with an informational notice that `{module}` is absent from the
file pattern. The inverse case is shown as **Solo archivo → clase**. Quick fixes are offered only for
directions that can be calculated without guessing.

## PHP declarations and namespaces

The inspection runs once per recognized `PhpFile` and considers supported top-level class
declarations. Declarations that do not satisfy an applicable class pattern are excluded.

- one exact match makes the existing pair valid;
- one candidate without an exact match gets compatible diagnostics and quick fixes;
- several candidates without a match produce one ambiguous diagnostic and no automatic choice;
- when named alternatives are enabled, each action identifies its declaration;
- several exact matches are all accepted;
- PHP files without candidate classes and non-PHP PSI files are ignored.

A pattern without namespace syntax applies only to global legacy classes. A rule participates for a
namespaced declaration only when the class pattern explicitly contains `\` or `sep(\)`:

```text
Class: {namespace:pascal|sep(\)}\{name:pascal}_Model
File:  src/{namespace:lower|sep(/)}/{name:snake}.model.php
```

This is explicit mapping data; the plugin does not duplicate or infer PSR-4.

## Built-in rule packs

The bundled catalog materializes ordinary editable rules for:

- PEAR / PSR-0;
- Zend Framework 1 application resources;
- Laravel 3 application directories;
- CodeIgniter 2 application directories.

Rule packs are creation-time bundles, not resolver modes. Apply a pack, then edit, reorder,
duplicate, chain, or remove its flat rules. Project-specific conventions belong in custom rules.

## Configure mappings

1. Open **Settings | Languages & Frameworks | Legacy Autoload Mapping**.
2. Select or create a mapping scheme.
3. Add a custom rule or use **Add from…** for a bundled rule pack.
4. Select an existing base directory. The saved value is relative to the project directory; `.` or
   an empty value represents the project directory itself.
5. Review the displayed directional capability and any informational notice.

The special **Project** scheme and the selected scheme ID are stored in
`.idea/legacyAutoload.xml`. **Default** and custom IDE schemes are stored in PhpStorm's application
settings and can be reused by other projects because every base directory remains project-relative.
Use **Copy to IDE…** or **Copy to Project…** to move a configuration between scopes. Only custom IDE
schemes can be renamed or deleted. Exported `*.legacy-autoload.xml` files use a validated versioned
interchange format. Because the plugin has not been published, the current storage and DSL do not
include compatibility parsing for earlier experimental formats.

## Inspection and quick fixes

Configure **Legacy autoload class/file mismatch** under
**Settings | Editor | Inspections | PHP**. It provides refactoring-based actions to:

- rename a candidate class to the expected declared name;
- move and, when needed, rename a PHP file to an expected mapped path.

Moves require an existing target directory and never overwrite another file. Ambiguous rules or
declarations never select an action silently. The optional rule policy exposes named alternatives.

The plugin does not suppress PhpStorm's PSR-0/PSR-4 inspection automatically. The settings page can
open that inspection or explicitly disable it for a project-level profile.

## Architecture

```text
mapping/value/                 captured structured values
mapping/transformation/        case, separator, and restriction pipeline
mapping/pattern/               bounded literal/token parsing
mapping/                       capabilities and pure class/file resolution
resolver/                      base-directory and alternative evaluation
inspection/                    recognized PHP PSI and file-level diagnostics
quickfix/                      refactoring-only mutations
settings/                      persistent rules and scheme codec
ui/                            schemes, rule packs, capabilities, and rule editor
```

The core resolver has no PHP PSI dependency. Pattern matching is bounded to 512 pattern characters,
16 variables, and 10,000 guarded search steps. Compiled mappings are cached per project.

## Build and verify

Requirements are JDK 21 or newer and network access on the first build for the PhpStorm SDK.

```bash
./gradlew test
./gradlew verifyPluginProjectConfiguration verifyPluginStructure
./gradlew buildPlugin
./gradlew verifyPlugin
```

The installable ZIP is generated in `build/distributions/`. The plugin targets PhpStorm 2026.1
(IntelliJ build 261) and newer and does not declare an upper IDE build bound.

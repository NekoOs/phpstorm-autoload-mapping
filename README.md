# Legacy Autoload Mapping for PhpStorm

Legacy Autoload Mapping teaches PhpStorm to resolve PHP classes that use deterministic legacy
class-to-file conventions the IDE does not understand natively.

It is intended for applications that predate modern Composer/PSR-4 layouts, including projects
whose autoloaders are implemented with `spl_autoload_register()`. It does not require namespaces,
Composer metadata, or changes to the application's architecture.

PSR-0 is one supported preset, not the plugin's data model.

## How mappings work

Each project-level `LegacyAutoloadMapping` contains four pieces of information:

- a descriptive name;
- a project-relative `SourceRoot`;
- a declarative `ClassPattern`;
- a declarative `FilePattern`.

Patterns combine fixed literals with variables. A variable can apply a registered transformation:

```text
Class pattern: {class}_Model
File pattern:  {class:kebab}.model.php
```

This maps in both directions:

```text
MyClass_Model ↔ my-class.model.php
```

Another mapping can be expressed as:

```text
Class pattern: {namespace:_}_{class}
File pattern:  {namespace:/}/{class}.php
```

which produces:

```text
My_Namespace_Example ↔ My/Namespace/Example.php
```

The initial pattern language is deliberately small. It supports fixed prefix/suffix literals and
these transformations:

| Transformation | Example |
| --- | --- |
| `underscorePath` or `/` | `Foo_Bar` ↔ `Foo/Bar` |
| `_` | Identity alias useful in class patterns |
| `kebab` | `MyClass` ↔ `my-class` |
| `snake` | `CustomerOrder` ↔ `customer_order` |
| `lower` | `Database` ↔ `database` for simple class names |

Transformations implement a small interface and are supplied through a registry. Adding one does
not require modifying either the mapping resolver or the project resolver.

## Representative conventions

The bundled presets are implemented by the same generic pattern engine:

| Class | File | Preset or pattern |
| --- | --- | --- |
| `Foo_Bar_Baz` | `Foo/Bar/Baz.php` | PSR-0 / PEAR |
| `App_Dao_Report_ReportExecutions` | `App/Dao/Report/ReportExecutions.php` | PSR-0 used by ZF1-style code |
| `Doctrine_Record_Abstract` | `Doctrine/Record/Abstract.php` | PSR-0 used by Doctrine 1-style code |
| `MyClass_Model` | `MyClass.model.php` | `_Model` → `.model.php` |
| `MyClass_Model` | `my-class.model.php` | `_Model` plus `kebab` |
| `CustomerController` | `controllers/customer.controller.php` | fixed directory and `snake` |
| `MyClass` | `MyClass.cls.php` | fixed file suffix |
| `Database` | `class.database.php` | fixed prefix and `lower` |

Framework names are documentation context only. The mapping engine does not contain ZF1, Doctrine,
or other framework-specific branches.

Arbitrary explicit maps are intentionally outside this model:

```text
Foo → weird/place/banana.php
Bar → legacy/x7.php
```

Those mappings are data lookups rather than deterministic naming transformations and should be
implemented by a separate responsibility.

## Configure mappings

1. Open **Settings | Languages & Frameworks | Legacy Autoload Mapping**.
2. Select **Add**.
3. Choose a bundled preset or edit the class and file patterns directly.
4. Select an existing project directory as the source root using the directory chooser.
5. Give the mapping a recognizable name and apply the settings.

Mappings are evaluated in list order; mappings with more specific source roots take precedence.
They persist in `.idea/legacyAutoload.xml` as project settings.

For the original ZF1/PSR-0 behavior, choose **PSR-0 / PEAR underscores to directories** and select a
root such as:

```text
website/application/classes
tests/unit
```

Create one mapping per source root.

## Inspection and quick fixes

The **Legacy autoload class/file mismatch** inspection validates top-level global PHP classes covered
by a configured mapping. It ignores namespaced classes, interfaces, traits, enums, anonymous
classes, PHP files without classes, and files outside mapped roots.

The inspection participates in PhpStorm's normal severity, scope, highlighting, and Alt+Enter
infrastructure. It provides:

- **Rename class to expected legacy autoload name**, using PhpStorm's rename refactoring so code
  references are updated safely;
- **Move file to mapped autoload path** when the target is unambiguous, its directory exists, and no
  file would be overwritten.

Configure it under
**Settings | Editor | Inspections | PHP | Legacy autoload class/file mismatch**.

The plugin does not silently suppress or replace PhpStorm's official PSR-0/PSR-4 inspection. When
legacy mappings are configured and the official inspection remains enabled, the settings page shows
a warning with an action that opens the official inspection directly. If the project uses a
project-level inspection profile, the warning also offers an explicit, confirmed action to disable
the official inspection for that project. This is optional because the same project may still
contain normal PSR-4 code that benefits from PhpStorm's inspection.

## Architecture

```text
mapping/
  LegacyAutoloadMapping          declarative mapping value
  LegacyAutoloadResolver         class ↔ relative-file resolution
  SourceRoot                     normalized project-relative root
  pattern/                       ClassPattern, FilePattern, template parser
  transformation/                open registry and reusable transformations
resolver/
  ProjectLegacyAutoloadResolver  project/VFS coordination
inspection/                      PHP PSI validation
quickfix/                        refactoring-based fixes
settings/                        persistent mapping state
ui/                              mappings, presets, patterns, directory chooser
```

The core has no PHP PSI dependency. The project resolver handles VFS roots, the inspection handles
PHP declarations, and quick fixes only handle refactoring. This keeps pattern parsing, autoload
resolution, IDE integration, and mutation as separate responsibilities.

## Build and run

Requirements:

- JDK 21 or newer; the Gradle toolchain resolver can provision JDK 21 automatically;
- network access on the first build to resolve the PhpStorm SDK.

```bash
./gradlew test
./gradlew runIde
```

`runIde` launches an isolated PhpStorm development instance. Release checks:

```bash
./gradlew clean test verifyPluginProjectConfiguration verifyPluginStructure buildPlugin
./gradlew verifyPlugin
```

The installable ZIP is generated in `build/distributions/`. Install it through
**Settings | Plugins | Install Plugin from Disk**.

The plugin targets PhpStorm 2026.1 (IntelliJ build 261) and newer, uses the public PHP PSI API from
`com.jetbrains.php`, and has no upper IDE build bound.

It does not modify Composer configuration, convert legacy classes to namespaces, or globally
suppress PhpStorm inspections.

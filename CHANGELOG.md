# Changelog

All notable changes to PHP Autoload Mapping are documented in this file.

## [Unreleased]

## [261.1.0] - 2026-08-27

### Changed

- Established the public product identity as PHP Autoload Mapping, with the native PhpStorm
  inspection treated as an optional compatibility integration rather than the plugin's identity.
- Replaced flat string substitutions with canonical structured values that preserve path,
  underscore, hyphen, dot, and namespace component boundaries.
- Reduced the DSL to `raw`, `lower`, `upper`, `pascal`, `camel`, `snake`, `kebab`, `dot`, explicit
  `sep(...)`, `length(n)`, and `nonempty`; experimental aliases are no longer accepted.
- Exposed pair validation, class-to-file derivation, and file-to-expected-name derivation as
  independent rule capabilities with notices for missing variables or ambiguous extraction.
- Changed inspection evaluation from one visit per class to one decision per recognized PHP file,
  preserving all applicable roots, alternative rules, fallback results, and top-level candidates.
- Limited namespace participation to rules whose class pattern expresses namespace structure.
- Renamed the project-relative rule location from source root to base directory to avoid implying an
  absolute path or a PhpStorm source-folder setting.
- Replaced project-local named profiles with PhpStorm-style scheme scopes: one Project scheme plus
  reusable Default/custom IDE schemes and explicit Copy to IDE/Copy to Project actions.

### Added

- Prepared the first public JetBrains Marketplace release, built against PhpStorm 2026.1.
- Ambiguous multi-class diagnostics with no automatic selection and optional quick fixes named by
  declaration.
- Coverage for canonical case inversion, structural separators, literals, partial capabilities,
  real adjacent-token ambiguity, namespaces, multiple declarations, non-PHP files, alternatives,
  fallback rules, and nested roots.

### Internal development milestones

The following milestones were pre-Marketplace snapshots, not published plugin versions.

#### Rule and scheme foundation

##### Changed

- Limited built-in rule packs to documented conventions and complete application schemas rather
  than illustrative transformations: PEAR/PSR-0, ZF1, Laravel 3, and CodeIgniter 2.
- Kept project-specific suffix, prefix, filename, and case rules available through Custom mappings
  instead of presenting them as historical standards.
- Separated rule-pack application from the single-rule editor. Rule packs now insert all of their
  rules as independent editable rows without retaining hidden ownership.
- Replaced the vertically wasteful rule-pack dialog with a visible **Add from…** control above the
  rule table, following PhpStorm's native scheme/source-selection pattern.
- Stopped deriving fallback names from the selected fallback, preventing accumulated labels such as
  `fallback 1 fallback 2`; chain roles are rendered separately from the rule name.
- Restored a compact rule form by moving the long ambiguous-quick-fix option out of the shared label
  column.

##### Added

- Controlled canonical case formats, explicit structural separators, fixed lengths, non-empty
  restrictions, and canonical reverse transformations.
- Bounded pipelines of up to four declarative stages used by framework rule packs and custom rules.
- Stable flat rule identities, explicit ordered fallback chains, rule duplication, and named
  alternative quick fixes.
- Compound rule-pack infrastructure with grouped providers that materializes ordinary independent
  mapping rows and leaves room for versioned imported/community sources.
- A multi-rule Zend Framework 1 application pack covering resource directories and default
  controllers without modeling `Zend_*` framework-internal classes.
- Multi-rule Laravel 3 and CodeIgniter 2 application schemas based on their historical application
  layouts rather than on a generic PSR label.
- Forward class-to-file validation for mappings whose file pattern cannot be reversed uniquely.
- Named persistent mapping schemes with an active-scheme selector.
- Scheme duplication, renaming, deletion, and validated versioned XML import/export for sharing
  complete community rule configurations.
- A discoverable scheme-management header modeled after PhpStorm's Code Style settings.
- Selection-aware toolbar actions: duplicate and fallback are disabled until a rule is selected.
- Independent directional capabilities for pair validation, class-to-file paths, and expected
  declaration names from files.
- Explicit `pascal|sep(_)` and `lower|sep(/)` composition for Laravel-style structural segments.

##### Performance

- Compiled mappings are cached per project and rebuilt only when project or selected IDE scheme settings change.
- Resolution filters by configured base directories and uses direct VFS paths without adding a custom
  PhpStorm index or scanning the project.

#### Inspection compatibility

##### Added

- A settings warning when PhpStorm's built-in PSR-0/PSR-4 inspection may report classes covered by
  configured legacy mappings.
- Direct navigation to the official inspection and an explicitly confirmed project-profile action
  to disable it safely.

##### Changed

- Improved alignment and spacing in the project settings UI.

#### Initial mapping prototype

##### Added

- Declarative, bidirectional legacy autoload class/file mappings.
- Integrated PSR-0/PEAR, model suffix, controller suffix, fixed prefix, and case transformation
  presets.
- Project-level mapping configuration with directory selection.
- PHP path mismatch inspection and refactoring-based rename/move quick fixes.
- Automated mapping, settings, inspection, and compatibility tests.

[261.1.0]: https://github.com/NekoOs/phpstorm-autoload-mapping/tree/v261.1.0
[Unreleased]: https://github.com/NekoOs/phpstorm-autoload-mapping/compare/v261.1.0...HEAD

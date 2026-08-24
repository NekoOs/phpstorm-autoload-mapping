# Changelog

All notable changes to Legacy Autoload Mapping are documented in this file.

## [0.2.1] - 2026-08-24

### Added

- A settings warning when PhpStorm's built-in PSR-0/PSR-4 inspection may report classes covered by
  configured legacy mappings.
- Direct navigation to the official inspection and an explicitly confirmed project-profile action
  to disable it safely.

### Changed

- Improved alignment and spacing in the project settings UI.

## [0.2.0] - 2026-08-24

### Added

- Declarative, bidirectional legacy autoload class/file mappings.
- Integrated PSR-0/PEAR, model suffix, controller suffix, fixed prefix, and case transformation
  presets.
- Project-level mapping configuration with directory selection.
- PHP path mismatch inspection and refactoring-based rename/move quick fixes.
- Automated mapping, settings, inspection, and compatibility tests.

[0.2.1]: https://github.com/NekoOs/phpstorm-legacy-autoload/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/NekoOs/phpstorm-legacy-autoload/releases/tag/v0.2.0

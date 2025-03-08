# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [21.1.2]

### Fixed
* Hopefully fixed problem where region refreshes could overlap if they took too long
* Raised `region_refresh_interval` default from 1200 to 6000 (5 minutes)

## [21.1.1]

### Added
* Added `region_refresh_interval` config setting to control how often the server checks for rift region refreshes
  * Default 1200 ticks (1 minute)

### Fixed
* Hopefully fix a CME occurring during region refresh checking

## [21.1.0]

### Added
* Initial public release

## Change Log
___

All notable changes to this project will be documented in this file.

___

## [1.1] - 2023-04-10

### Added

- [Batch](https://github.com/lebedev-artem/searchengine-master/blob/0623f920f6cdd3d09077b04e681452f4653e8c03/src/main/resources/application.yaml#L33) INSERT / UPDATE hibernate statement.
- Cascade deletion all dependent tables by **site**.
- In [application.yaml](https://github.com/lebedev-artem/searchengine-master/blob/f11a009daa1e161bb9ebc1fe85d767eef4ddc6ad/src/main/resources/application.yaml) added [properties](https://github.com/lebedev-artem/searchengine-master/blob/f11a009daa1e161bb9ebc1fe85d767eef4ddc6ad/src/main/resources/application.yaml#L76), that can be used to control certain actions with the table

### Fixed

- The controllers have been made *flat*.
- Improved performance due to the change in **id** generation type.

### Changed

- Change Id generation strategy to **SEQUENCE** for table **site**, **page**, **lemma**.
- Method parameters in the ApiController are made **final**.
- Refactoring the arrangement of classes in packages.

### Removed

- Composite key for **index** table removed.
- Reset **id** of records in the database removed.
- All **native SQL query** removed from repositories.




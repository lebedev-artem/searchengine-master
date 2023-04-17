## Change Log
___

All notable changes to this project will be documented in this file.

___

## [1.1] - 2023-04-10

### Added

- [Batch](https://github.com/lebedev-artem/searchengine-master/blob/0623f920f6cdd3d09077b04e681452f4653e8c03/src/main/resources/application.yaml#L33) INSERT / UPDATE hibernate statement.
- Cascade deletion all dependent tables by **site**.
- In [application.yaml](https://github.com/lebedev-artem/searchengine-master/blob/4cd80e1f636f6a14d77a0aff7f8f94b0276c470e/src/main/resources/application.yaml) added [properties](https://github.com/lebedev-artem/searchengine-master/blob/4cd80e1f636f6a14d77a0aff7f8f94b0276c470e/src/main/resources/application.yaml#L76), that can be used to control certain actions with the table
- Created [RepositoryService](https://github.com/lebedev-artem/searchengine-master/blob/4cd80e1f636f6a14d77a0aff7f8f94b0276c470e/src/main/java/searchengine/services/RepositoryService.java)

### Fixed

- The controllers have been made *flat*.
- Improved performance due to the change in **id** generation type.
- Calculating relevance fixed. The maximum relevance was calculated incorrectly.
- Add missing space between words while cleaning text for snippet generator.

### Changed

- Change Id generation strategy to **SEQUENCE** for table **site**, **page**, **lemma**.
- Method parameters in the ApiController are made **final**.
- Refactoring the arrangement of classes in packages.
- Now entities getting from DB in search using Pageable with limit and offset.

### Removed

- Composite key for **index** table removed.
- Reset **id** of records in the database removed.
- All **native SQL query** removed from repositories.




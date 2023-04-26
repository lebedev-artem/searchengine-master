## Change Log
___

All notable changes to this project will be documented in this file.

___

## [1.2] - 2023-04-25

### Added

- Added cleanup of page content with Jsoup.Clean before uploading to the database, which reduced the size of the uploaded data to DB by an average of 7 times (minimum 2, maximum about 15)

### Fixed

- Checked & fixed some code according to [design](https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_rules_java.html#design), [code style](https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_rules_java.html#code-style) and other rules of [PMD (static source code analyzer)](https://docs.pmd-code.org/pmd-doc-6.55.0/index.html)
- Duplicate code in some class removed.
- Change sequence of deletion **[pages](https://github.com/lebedev-artem/searchengine-master/blob/fbacc375cc12f2e8c48b7f905e4f4cf477a079f5/src/main/java/searchengine/tools/indexing/SchemaActions.java#L196)** and **lemmas** in **[partialInit](https://github.com/lebedev-artem/searchengine-master/blob/fbacc375cc12f2e8c48b7f905e4f4cf477a079f5/src/main/java/searchengine/tools/indexing/SchemaActions.java#L100)**, causing the exception updating foreign key constraint.

### Changed

- Reduce complexity of methods.
- Add _import static searchengine.tools.StringPool.*;_ to keep the clean code.

### Removed

- Unnecessary code in the parser when stopped is removed.

___

## [1.1] - 2023-04-19

### Added

- [Batch](https://github.com/lebedev-artem/searchengine-master/blob/0623f920f6cdd3d09077b04e681452f4653e8c03/src/main/resources/application.yaml#L33) INSERT / UPDATE hibernate statement.
- Cascade deletion all dependent tables by **site**.
- In [application.yaml](https://github.com/lebedev-artem/searchengine-master/blob/4cd80e1f636f6a14d77a0aff7f8f94b0276c470e/src/main/resources/application.yaml) added [properties](https://github.com/lebedev-artem/searchengine-master/blob/4cd80e1f636f6a14d77a0aff7f8f94b0276c470e/src/main/resources/application.yaml#L76), that can be used to control certain actions with the table
- Created [RepositoryService](https://github.com/lebedev-artem/searchengine-master/blob/4cd80e1f636f6a14d77a0aff7f8f94b0276c470e/src/main/java/searchengine/services/RepositoryService.java)

### Fixed

- The controllers have been made *flat*.
- Improved performance approx. x2 due to the change in **id** generation type.
- Calculating relevance fixed. The maximum relevance was calculated incorrectly.
- Add missing space between words while cleaning text for snippet generator.

### Changed

- Change Id generation strategy to **SEQUENCE** for table **site**, **page**, **lemma**.
- Method parameters in the ApiController are made **final**.
- Refactoring the arrangement of classes in packages.

### Removed

- Composite key for **index** table removed.
- Reset **id** of records in the database removed.
- All **native SQL query** removed from repositories.
- Pageable getting results from DB removed, because it is not possible to correctly count the number of results when searching for all sites




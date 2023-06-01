## Change Log
___

All notable changes to this project will be documented in this file.

___

## [1.4] - 2023-06-02

### Added

### Fixed

### Removed

- IndexingActions class removed. All methods moved to IndexingService

### Changed

- LemmaIndexCollector not @Service now
- ScrapAction renamed to CrawlAction

___

## [1.4] - 2023-05-24

### Added


### Fixed

-  Fixed methods of getting path to be able to parse sites like http://www.sitename.com/level1/

### Changed

- ID generation for entities changed to IDENTITY

### Removed

- Static boolean variable removed from services
- RepositoryService removed
- Class for PageEntity saving removed and went to FJP

___

## [1.3] - 2023-05-08

### Added

- In [application.yaml](https://github.com/lebedev-artem/searchengine-master/blob/7835ef659c65c80a57a62a8284f485751496fa7f/src/main/resources/application.yaml) added [delete-most-frequently-lemmas](https://github.com/lebedev-artem/searchengine-master/blob/e46dbe16e96e145b17327a85622d92bb8cb2fe88/src/main/resources/application.yaml#L87), [return-zero-pages-if-not-all-lemmas-found](https://github.com/lebedev-artem/searchengine-master/blob/e46dbe16e96e145b17327a85622d92bb8cb2fe88/src/main/resources/application.yaml#L88) properties.

### Fixed

- Fixed the problem of writing an incorrect site url after indexing a separate page
- The error during the search for occurrences of remaining lemmas on pages has been fixed. Change [set of pages](https://github.com/lebedev-artem/searchengine-master/blob/e46dbe16e96e145b17327a85622d92bb8cb2fe88/src/main/java/searchengine/services/Impl/SearchServiceImpl.java#L132) .
    
### Changed

- Zero pages may be returned if [not all lemmas exists on site](https://github.com/lebedev-artem/searchengine-master/blob/7835ef659c65c80a57a62a8284f485751496fa7f/src/main/java/searchengine/services/Impl/SearchServiceImpl.java#L108) (see property in *.yaml)

### Removed

___

## [1.2] - 2023-04-29

### Added

- Added [cleanup](https://github.com/lebedev-artem/searchengine-master/blob/3daeaa493cde218360e489aba1c5c7de0d44329b/src/main/java/searchengine/tools/indexing/ScrapingAction.java#L152) of page content with Jsoup.Clean before uploading to the database, which reduced the size of the uploaded data to DB by an average of 7 times (minimum 2, maximum about 15)
- Method **[sleeping(int millis, String s)](https://github.com/lebedev-artem/searchengine-master/blob/3daeaa493cde218360e489aba1c5c7de0d44329b/src/main/java/searchengine/services/Impl/LemmasAndIndexCollectingServiceImpl.java#L71)** was created to call the sleep() method in a catch block with arguments, which can be used in different code sections.
- Lombok **@Builder** for DTO of statistics items added.


### Fixed
- Trying to reduce count of methods lines (complexity of methods).
- Checked & fixed some code according to [design](https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_rules_java.html#design), [code style](https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_rules_java.html#code-style) and other rules of [PMD (static source code analyzer)](https://docs.pmd-code.org/pmd-doc-6.55.0/index.html)
- Duplicate code in some class removed.
- Change sequence of deletion **[pages](https://github.com/lebedev-artem/searchengine-master/blob/fbacc375cc12f2e8c48b7f905e4f4cf477a079f5/src/main/java/searchengine/tools/indexing/SchemaActions.java#L196)** and **lemmas** in **[partialInit](https://github.com/lebedev-artem/searchengine-master/blob/fbacc375cc12f2e8c48b7f905e4f4cf477a079f5/src/main/java/searchengine/tools/indexing/SchemaActions.java#L100)**, causing the exception updating foreign key constraint.
- Implemented the **RepositoryService** dependency in the **LemmasAndIndexCollectingService**.
- Unnecessary code in the parser when stopped is removed.
- Optimizing the code has increased the parsing speed by about 20%

|  **Before** | `7 site(s) 2026 pages 54012 lemmas 613535 index entries Just in 888329 ms`  |
|---|-----------------------------------------------------------------------------|
| **After**  | `7 site(s) 2035 pages 54093 lemmas 621423 index entries Just in 648315 ms ` |

### Changed

- Add _import static searchengine.tools.StringPool.*;_ to keep the clean code.

### Removed

- StaticVault for lemmas and index entities removed.



___

## [1.1] - 2023-04-19

### Added

- [Batch](https://github.com/lebedev-artem/searchengine-master/blob/0623f920f6cdd3d09077b04e681452f4653e8c03/src/main/resources/application.yaml#L33) INSERT / UPDATE hibernate statement.
- Cascade deletion all dependent tables by **site**.
- In [application.yaml](https://github.com/lebedev-artem/searchengine-master/blob/4cd80e1f636f6a14d77a0aff7f8f94b0276c470e/src/main/resources/application.yaml) added [properties](https://github.com/lebedev-artem/searchengine-master/blob/4cd80e1f636f6a14d77a0aff7f8f94b0276c470e/src/main/resources/application.yaml#L76), that can be used to control certain actions.
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




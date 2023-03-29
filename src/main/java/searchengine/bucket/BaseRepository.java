//package searchengine.bucket;
//
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Modifying;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.NoRepositoryBean;
//
//import java.io.Serializable;
//
//@NoRepositoryBean
//public interface BaseRepository<T, ID extends Serializable> extends JpaRepository<T, ID> {
//
////		void delete(T entity);
//
//	@Modifying(clearAutomatically = true, flushAutomatically = true)
//	@Query(value = "ALTER TABLE `site` AUTO_INCREMENT = 1", nativeQuery = true)
//	void resetIdOnSiteTable();
//
//	@Modifying(clearAutomatically = true, flushAutomatically = true)
//	@Query(value = "ALTER TABLE `page` AUTO_INCREMENT = 1", nativeQuery = true)
//	void resetIdOnPageTable();
//
//	@Modifying(clearAutomatically = true, flushAutomatically = true)
//	@Query(value = "ALTER TABLE `lemma` AUTO_INCREMENT = 1", nativeQuery = true)
//	void resetIdOnLemmaTable();
//
//	@Modifying(clearAutomatically = true, flushAutomatically = true)
//	@Query(value = "ALTER TABLE `search_index` AUTO_INCREMENT = 1", nativeQuery = true)
//	void resetIdOnIndexTable();
//
//}

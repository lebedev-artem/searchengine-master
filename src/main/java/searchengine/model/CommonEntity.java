package searchengine.model;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.stereotype.Repository;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class CommonEntity {

	@Id
	int id;

}

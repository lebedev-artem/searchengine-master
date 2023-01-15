package searchengine.services.indexing;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

@Getter
@Setter
@RequiredArgsConstructor
public class Task {
	private final String URL;
	private final ArrayList<Task> subTasks = new ArrayList<>();
	private int level = 0;
	private final Map<String, Integer> links = new TreeMap<>();

	public void addSubTask(Task task) {
		task.setLevel(level + 1);
		subTasks.add(task);
	}
}

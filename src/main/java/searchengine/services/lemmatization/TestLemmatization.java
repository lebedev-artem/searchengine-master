package searchengine.services.lemmatization;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.List;

public class TestLemmatization {
	public static void main(String[] args) throws IOException {
		LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
		List<String> wordBaseForms = luceneMorphology.getNormalForms("леса");
		wordBaseForms.forEach(System.out::println);
		String testText = "Повторное появление леопарда в Осетии позволяет предположить,\n" +
				"что леопард постоянно обитает в некоторых районах Северного\n" +
				"Кавказа.";

	}

	public void lemmaExtractor(String textForAnalyzing){

	}

}

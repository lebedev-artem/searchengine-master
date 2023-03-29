package searchengine.exceptionhandler;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler{

	@ExceptionHandler(EmptyQueryException.class)
	public ResponseEntity<AwesomeException> emptyQuery(){
		return new ResponseEntity<>(new AwesomeException(false, "Empty query"), HttpStatus.BAD_REQUEST);
	}

	@Data
	@AllArgsConstructor
	private static class AwesomeException {
		private boolean result;
		private String error;
	}
}

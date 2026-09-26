package io.github.jobexplorer.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.jobexplorer.config.CandidateProfile;
import io.github.jobexplorer.service.ModelCallException;
import io.github.jobexplorer.service.LlmRequirementExtractor;
import io.github.jobexplorer.service.ModelNotConfiguredException;
import io.github.jobexplorer.service.NotFoundException;
import io.github.jobexplorer.web.Dtos.ErrorBody;
import io.github.jobexplorer.web.Dtos.SystemStatus;

@RestController
@RequestMapping("/api")
public class SystemController {

	private final CandidateProfile profile;

	private final LlmRequirementExtractor extractor;

	public SystemController(CandidateProfile profile, LlmRequirementExtractor extractor) {
		this.profile = profile;
		this.extractor = extractor;
	}

	@GetMapping("/status")
	public SystemStatus status() {
		return new SystemStatus(extractor.isAvailable(), profile.name());
	}

	/** 把业务异常翻译成前端能直接显示的中文提示。 */
	@RestControllerAdvice
	static class Errors {

		@ExceptionHandler(NotFoundException.class)
		@ResponseStatus(HttpStatus.NOT_FOUND)
		ErrorBody notFound(NotFoundException ex) {
			return new ErrorBody(ex.getMessage());
		}

		@ExceptionHandler(ModelNotConfiguredException.class)
		@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
		ErrorBody noModel(ModelNotConfiguredException ex) {
			return new ErrorBody(ex.getMessage());
		}

		@ExceptionHandler(ModelCallException.class)
		@ResponseStatus(HttpStatus.BAD_GATEWAY)
		ErrorBody modelCallFailed(ModelCallException ex) {
			return new ErrorBody(ex.getMessage());
		}

		@ExceptionHandler(IllegalArgumentException.class)
		@ResponseStatus(HttpStatus.BAD_REQUEST)
		ErrorBody badRequest(IllegalArgumentException ex) {
			return new ErrorBody(ex.getMessage());
		}
	}
}

package io.github.jobexplorer.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.jobexplorer.agent.AgentService;
import io.github.jobexplorer.agent.AgentService.AgentAnswer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

	private final AgentService agent;

	public AgentController(AgentService agent) {
		this.agent = agent;
	}

	@PostMapping("/ask")
	public AgentAnswer ask(@Valid @RequestBody Question q) {
		return agent.ask(q.question());
	}

	public record Question(@NotBlank @Size(max = 500) String question) {
	}
}

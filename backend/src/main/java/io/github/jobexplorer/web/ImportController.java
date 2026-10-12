package io.github.jobexplorer.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.jobexplorer.service.SampleBundle;
import io.github.jobexplorer.service.SampleImportService;
import io.github.jobexplorer.service.SampleImportService.ImportReport;

/** 导入本地样本（JSON），返回导入报告。不调用大模型。 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

	private final SampleImportService importService;

	public ImportController(SampleImportService importService) {
		this.importService = importService;
	}

	@PostMapping
	public ImportReport importBundle(@RequestBody SampleBundle bundle) {
		return importService.importBundle(bundle);
	}
}

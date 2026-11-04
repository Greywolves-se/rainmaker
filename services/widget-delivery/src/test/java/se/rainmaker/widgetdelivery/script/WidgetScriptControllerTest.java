package se.rainmaker.widgetdelivery.script;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WidgetScriptControllerTest {

  private static final String SCRIPT = "(function(){\"use strict\";})();\n";

  @TempDir Path folder;

  private MockMvc serving(Path script) {
    WidgetScriptProperties properties = new WidgetScriptProperties(script.toUri().toString());
    return MockMvcBuilders.standaloneSetup(
            new WidgetScriptController(properties, new DefaultResourceLoader()))
        .build();
  }

  @Test
  void servesTheBuiltScriptAsJavaScript() throws Exception {
    Path script = Files.writeString(folder.resolve("rainmaker.js"), SCRIPT);

    serving(script)
        .perform(get("/widget/rainmaker.js"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/javascript;charset=UTF-8"))
        .andExpect(content().string(SCRIPT));
  }

  @Test
  void letsBrowsersCheckForNewerBuildOnEveryLoad() throws Exception {
    Path script = Files.writeString(folder.resolve("rainmaker.js"), SCRIPT);

    serving(script)
        .perform(get("/widget/rainmaker.js"))
        .andExpect(header().string("Cache-Control", "no-cache"));
  }

  @Test
  void answersNotFoundWhenTheWidgetHasNotBeenBuilt() throws Exception {
    serving(folder.resolve("rainmaker.js"))
        .perform(get("/widget/rainmaker.js"))
        .andExpect(status().isNotFound());
  }

  @Test
  void picksUpNewBuildWithoutRestart() throws Exception {
    Path script = folder.resolve("rainmaker.js");
    MockMvc mockMvc = serving(script);
    mockMvc.perform(get("/widget/rainmaker.js")).andExpect(status().isNotFound());

    Files.writeString(script, SCRIPT);

    mockMvc.perform(get("/widget/rainmaker.js")).andExpect(status().isOk());
  }

  @Test
  void servesNothingElseUnderWidget() throws Exception {
    Path script = Files.writeString(folder.resolve("rainmaker.js"), SCRIPT);
    Files.writeString(folder.resolve("other.js"), SCRIPT);

    serving(script).perform(get("/widget/other.js")).andExpect(status().isNotFound());
  }
}

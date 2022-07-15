package de.heisluft.modding.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class IdeaRunConfigMaker extends DefaultTask {
  @Input
  @Optional
  public abstract Property<String> getConfigName();

  @Input
  @Optional
  public abstract Property<String> getWorkDir();

  @Input
  public abstract Property<String> getMainClassName();

  @Input
  @Optional
  public abstract Property<String> getTargetSourceSet();

  @Input
  @Optional
  public abstract Property<String> getTargetProject();

  @Input
  @Optional
  public abstract Property<Boolean> getBuildBefore();

  @Input
  @Optional
  public abstract ListProperty<String> getAppArgs();

  @Input
  @Optional
  public abstract ListProperty<String> getJvmArgs();

  @Input
  @Optional
  public abstract MapProperty<String, String> getEnvVars();

  @OutputFile
  public File getOutput() {
    return new File(getProject().getRootDir(), ".idea/runConfigurations/" + getConfigName().get() + ".xml");
  }

  public IdeaRunConfigMaker() {
    getTargetSourceSet().convention("main");
    getTargetProject().convention(getProject().getName());
    getConfigName().convention(getMainClassName().map(n -> n.contains(".") ? n.substring(n.lastIndexOf('.') + 1) : n));
    getBuildBefore().convention(true);
    getAppArgs().convention(Collections.emptyList());
    getJvmArgs().convention(Collections.emptyList());
    getEnvVars().convention(Collections.emptyMap());
  }

  @TaskAction
  public void generate() throws TransformerException, ParserConfigurationException, IOException {
    Map<String, String> envVars = getEnvVars().get();
    List<String> cliArgs = getAppArgs().get();
    List<String> jvmArgs = getJvmArgs().get();
    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    Element rootTag = doc.createElement("component");
    doc.appendChild(rootTag);

    Element configurationTag = doc.createElement("configuration");
    rootTag.appendChild(configurationTag);
    configurationTag.setAttribute("default", "false");
    configurationTag.setAttribute("factoryName", "Application");
    configurationTag.setAttribute("name", getConfigName().get());
    configurationTag.setAttribute("singleton", "false");
    configurationTag.setAttribute("type", "Application");

    if(!envVars.isEmpty()) {
      Element envsTag = doc.createElement("envs");
      rootTag.appendChild(envsTag);
      envVars.forEach((k, v) -> {
        Element envTag = doc.createElement("env");
        envsTag.appendChild(envTag);
        envTag.setAttribute("name", k);
        envTag.setAttribute("value", v);
      });
    }

    Element moduleTag = doc.createElement("module");
    configurationTag.appendChild(moduleTag);
    moduleTag.setAttribute("name", getTargetProject().get() + "." + getTargetSourceSet().get());

    Element mainClassTag = doc.createElement("option");
    configurationTag.appendChild(mainClassTag);
    mainClassTag.setAttribute("name", "MAIN_CLASS_NAME");
    mainClassTag.setAttribute("value", getMainClassName().get());

    if(getWorkDir().isPresent()) {
      Element workDirTag = doc.createElement("option");
      configurationTag.appendChild(workDirTag);
      workDirTag.setAttribute("name", "WORKING_DIRECTORY");
      workDirTag.setAttribute("value", getWorkDir().get());
    }

    if(!cliArgs.isEmpty()) {
      Element progParamsTag = doc.createElement("option");
      configurationTag.appendChild(progParamsTag);
      progParamsTag.setAttribute("name", "PROGRAM_PARAMETERS");
      progParamsTag.setAttribute("value", String.join(" ", cliArgs));
    }

    if(!jvmArgs.isEmpty()) {
      Element jvmParamsTag = doc.createElement("option");
      configurationTag.appendChild(jvmParamsTag);
      jvmParamsTag.setAttribute("name", "VM_PARAMETERS");
      jvmParamsTag.setAttribute("value", String.join(" ", jvmArgs));
    }

    Transformer t = TransformerFactory.newInstance().newTransformer();
    t.setOutputProperty(OutputKeys.INDENT, "yes");
    t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    t.transform(new DOMSource(doc), new StreamResult(new FileOutputStream(getOutput())));
  }
}
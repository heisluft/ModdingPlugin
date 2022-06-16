package de.heisluft.modding.tasks;

import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.base.internal.compile.Compiler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ODJavaCompile extends JavaCompile {

  public void hackAndCompile() {
    JavaToolchainService service = getProject().getExtensions().getByType(JavaToolchainService.class);
    getJavaCompiler().set(service.compilerFor(s -> s.getLanguageVersion().set(JavaLanguageVersion.of(this.getSourceCompatibility()))));

    getOutputs().setPreviousOutputFiles(getProject().files());
    DefaultJavaCompileSpec spec = doCreateSpec();
    spec.setSourceFiles(getSource());
    setDidWork(doCreateCompiler().execute(spec).getDidWork());
  }

  private DefaultJavaCompileSpec doCreateSpec() {
    try {
      Method target = JavaCompile.class.getDeclaredMethod("createSpec");
      target.setAccessible(true);
      return (DefaultJavaCompileSpec) target.invoke(this);
    } catch(NoSuchMethodException e) {
      throw new RuntimeException("Could not find JavaCompile#createSpec method; Gradle internals may have changed in newer versions");
    } catch(IllegalAccessException e) {
      throw new RuntimeException("Could not access JavaCompile#createSpec method", e);
    } catch(InvocationTargetException e) {
      Throwable cause = e.getCause();
      if(cause instanceof RuntimeException) throw (RuntimeException) cause;
      if(cause instanceof Error) throw (Error) cause;
      throw new RuntimeException(e.getCause());
    }
  }
  @SuppressWarnings("unchecked")
  private Compiler<JavaCompileSpec> doCreateCompiler() {
    try {
      Method createCompiler = JavaCompile.class.getDeclaredMethod("createCompiler");
      createCompiler.setAccessible(true);
      return (Compiler<JavaCompileSpec>) createCompiler.invoke(this);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Could not find JavaCompile#createCompiler method; Gradle internals may have changed in newer versions");
    } catch(IllegalAccessException e) {
      throw new RuntimeException("Could not access JavaCompile#createCompiler method", e);
    } catch(InvocationTargetException e) {
      Throwable cause = e.getCause();
      if(cause instanceof RuntimeException) throw (RuntimeException) cause;
      if(cause instanceof Error) throw (Error) cause;
      throw new RuntimeException(e.getCause());
    }
  }
}

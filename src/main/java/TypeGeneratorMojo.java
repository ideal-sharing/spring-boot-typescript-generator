
import backend.EndPointParser;
import backend.spring.SpringEndpointParser;
import frontend.api.EndpointWriter;
import frontend.api.angular.AngularWriter;
import frontend.api.reactQuery.ReactQueryWriter;
import frontend.TypeScriptFile;
import frontend.types.TypeWriter;
import frontend.types.typescript.TypeScriptWriter;
import frontend.types.zod.ZodWriter;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import model.TypeContext;
import model.Endpoint;
import model.config.Backend;
import model.config.FrontendAPI;
import model.config.FrontendTypes;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


@Mojo(name = "type-generator", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class TypeGeneratorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Parameter(property = "subModules")
    String subModules;

    @Parameter(property = "outputDirectory", required = true)
    String outputDir;

    @Parameter(property = "backend", required = true)
    Backend backend;

    @Parameter(property = "frontendAPI", required = true)
    FrontendAPI frontendAPI;

    @Parameter(property = "frontendTypes", required = true)
    FrontendTypes frontendTypes;

    @Parameter(property = "useStringAsDate", defaultValue = "false")
    boolean useStringAsDate = false;


    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Type Generator Plugin");

        ClassPool classPool = ClassPool.getDefault();
        List<String> classFiles = new ArrayList<>();

        if(outputDir == null || outputDir.isBlank()){
            throw new MojoExecutionException("Output directory must not be empty");
        }
        try {
            if (subModules == null || subModules.equals("")) {
                classFiles.addAll(getClassFiles(new File(project.getBuild().getOutputDirectory()), project.getBuild().getOutputDirectory() + "/"));
                classPool.insertClassPath(project.getBuild().getOutputDirectory());
            } else {
                String[] moduleArray = subModules.split(",");
                for (String module : moduleArray) {
                    String stripped = module.strip();
                    if(project.getModules().contains(stripped)) {
                        getLog().info("Scanning module " + stripped);
                        // TODO this should not be hardcoded
                        String outputPath = project.getFile().getParentFile().getAbsoluteFile() + "/" + module + "/target/classes/";
                        classFiles.addAll(getClassFiles(new File(outputPath), outputPath));
                        classPool.insertClassPath(outputPath);
                    } else {
                        throw new RuntimeException("Module " + stripped + " not found in maven project");
                    }
                }
            }

            TypeContext context = new TypeContext(classPool, useStringAsDate);

            EndPointParser endPointParser = switch (backend) {
                case Spring -> new SpringEndpointParser(context);
            };

            List<Endpoint> endpoints = new ArrayList<>();

            for (String className : classFiles) {
                CtClass clazz = classPool.getCtClass(className);
                endpoints.addAll(endPointParser.parseClass(clazz));
            }

            String[] dirs = outputDir.split(",");

            for(String dir: dirs) {
                String dirName = dir.strip();
                if (!dirName.endsWith("/")) {
                    dirName += "/";
                }

                TypeWriter typeWriter = switch (frontendTypes) {
                    case Typescript -> new TypeScriptWriter(dirName);
                    case Zod -> new ZodWriter(dirName);
                };

                EndpointWriter endpointWriter = switch (frontendAPI) {
                    case ReactQuery -> new ReactQueryWriter(context, dirName);
                    case Angular -> new AngularWriter(context, dirName);
                };

                List<TypeScriptFile> files = new ArrayList<>();
                files.addAll(typeWriter.printAllTypes(context));
                files.addAll(endpointWriter.printAllEndPoints(endpoints));
                files.forEach(TypeScriptFile::write);
            }

        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getClassFiles(File file, String basePath) {
        if (!file.isDirectory() && file.getName().endsWith(".class")) {
            return List.of(stripName(file, basePath));
        }

        if (file.isDirectory()) {
            List<String> paths = new ArrayList<>();
            for (File child : Objects.requireNonNull(file.listFiles())) {
                paths.addAll(getClassFiles(child, basePath));
            }
            return paths;
        }

        return List.of();
    }

    private String stripName(File file, String basePath) {
       return file.getAbsolutePath()
                .replace(basePath, "")
                .replace('/', '.')
                .replace(".class", "");
    }
}

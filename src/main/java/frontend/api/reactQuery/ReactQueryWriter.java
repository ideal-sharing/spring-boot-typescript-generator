package frontend.api.reactQuery;

import frontend.api.EndpointWriter;
import frontend.types.TypeWriter;
import frontend.TypeScriptFile;
import lombok.RequiredArgsConstructor;
import model.TypeContext;
import model.Endpoint;
import model.types.*;
import org.springframework.http.HttpMethod;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ReactQueryWriter implements EndpointWriter {
    private final TypeContext context;
    private final String basePath;

    private final List<TypeScriptFile.Import> defaultImports = List.of(
            new TypeScriptFile.Import("axios", "axios", Set.of())
    );

    @Override
    public List<TypeScriptFile> printAllEndPoints(List<Endpoint> endpoints) {
        List<TypeScriptFile> files = new ArrayList<>();

        Map<String, List<Endpoint>> endpointMap = new HashMap<>();
        endpoints.forEach(endpoint ->
            endpointMap.computeIfAbsent(endpoint.getClassName(), k -> new ArrayList<>())
                    .add(endpoint)
        );

        endpointMap.forEach((className, classEndpoints) -> {
            TypeScriptFile typeScriptFile = new TypeScriptFile();
            typeScriptFile.getImports().addAll(defaultImports);

            TypeScriptFile.Import reactQueryImport = new TypeScriptFile.Import("@tanstack/react-query", null, new HashSet<>());

            if(classEndpoints.stream().anyMatch(e -> e.getHttpMethod().equals(HttpMethod.GET))) {
                reactQueryImport.getImports().addAll(Set.of("useQuery", "UseQueryOptions"));
            }

            if(classEndpoints.stream().anyMatch(e -> !e.getHttpMethod().equals(HttpMethod.GET))) {
                reactQueryImport.getImports().addAll(Set.of("useMutation", "UseMutationOptions"));
            }
            typeScriptFile.getImports().add(reactQueryImport);

            typeScriptFile.setLocation(basePath + ENDPOINTS_DIR + "/" + className);
            StringBuilder body = new StringBuilder("export default class " + className + " {\n");
            classEndpoints.stream().map(this::printEndPoint).forEach(body::append);
            classEndpoints.forEach(endpoint -> {
                if(endpoint.getBody() != null) {
                    typeScriptFile.addImport(endpoint.getBody(), context);
                }
                endpoint.getParams().forEach(field -> typeScriptFile.addImport(field.getType(), context));
                endpoint.getUrlArgs().forEach(field -> typeScriptFile.addImport(field.getType(), context));
                typeScriptFile.addImport(endpoint.getReturnType(), context);
            });
            body.deleteCharAt(body.length() - 1);
            body.append("}\n");
            typeScriptFile.setBody(body.toString());
            files.add(typeScriptFile);
        });

        return files;
    }

    private String printEndPoint(Endpoint endpoint) {
        if(endpoint.getHttpMethod().equals(HttpMethod.GET)){
            return printQuery(endpoint);
        } else {
            return printMutation(endpoint);
        }
    }

    private String printMutation(Endpoint endpoint) {
        List<Field> sortedParams = endpoint.getAllVariables();
        String returnType = "<" + TypeWriter.printType(endpoint.getReturnType(), context) + ">";

        String args = getFnParams(sortedParams);
        if(!args.isEmpty()) {
            args += ", ";
        }

        String genericParams;

        if(endpoint.getBody() != null) {
            genericParams = "<" + TypeWriter.printType(endpoint.getReturnType(), context) + ", unknown, " + TypeWriter.printType(endpoint.getBody(), context) + ">";
        } else {
            genericParams = "<" + TypeWriter.printType(endpoint.getReturnType(), context) + ">";
        }
        args += "options?: Omit<UseMutationOptions" + genericParams + ", 'mutationFn'>";

        StringBuilder method = new StringBuilder("  static " + endpoint.getName() + " = {\n    useMutation: (" + args + ") => ");
        method.append("useMutation").append(genericParams).append("(");
        method.append("async (");
        if(endpoint.getBody() != null) {
            method.append("data: ").append(TypeWriter.printType(endpoint.getBody(), context));
        }
        method.append( ") => {\n");
        method.append("      const response = await axios.").append(endpoint.getHttpMethod().name().toLowerCase()).append(returnType);
        method.append("(").append(formatUrl(endpoint));
        if(endpoint.getBody() != null) {
            method.append(", data");
        } else if(!endpoint.getHttpMethod().equals(HttpMethod.DELETE)) {
            method.append(", null");
        }
        if(!endpoint.getParams().isEmpty()) {
            method.append(", { params: ").append(printParams(endpoint.getParams())).append(" }");
        }
        method.append(");\n");

        method.append("      return response.data;\n");
        method.append("    }, options),\n");
        method.append("  };\n\n");
        return method.toString();
    }

    private String printQuery(Endpoint endpoint) {
        String key = endpoint.getClassName() + "_" + endpoint.getName();
        List<Field> sortedParams = endpoint.getAllVariables();
        String returnType = "<" + TypeWriter.printType(endpoint.getReturnType(), context) + ">";

        String args = getFnParams(sortedParams);
        if(!args.isEmpty()) {
            args += ", ";
        }
        args += "options?: Omit<Omit<UseQueryOptions" + returnType + ", 'queryKey'>, 'queryFn'>";

        StringBuilder method = new StringBuilder("  static " + endpoint.getName() + " = {\n    useQuery: (" + args + ") => useQuery" + returnType + "(");

        method.append("['").append(key).append("'");
        if(!sortedParams.isEmpty()) {
            method.append(", ").append(String.join(", ", sortedParams.stream().map(Field::getName).toList()));
        }
        method.append("], ");

        method.append("async (");
        method.append(") => {\n");
        method.append("      const response = await axios.").append(endpoint.getHttpMethod().name().toLowerCase()).append(returnType);
        method.append("(").append(formatUrl(endpoint));
        if(!endpoint.getParams().isEmpty()) {
            method.append(", { params: ").append(printParams(endpoint.getParams())).append(" }");
        }
        method.append(");\n");
        method.append("      return response.data;\n");
        method.append("    }, options),\n");
        method.append("  };\n\n");
        return method.toString();
    }


    private String formatUrl(Endpoint endpoint) {
        String url = endpoint.getUrl();
        for (Field urlArg : endpoint.getUrlArgs()) {
            url = url.replace("{" + urlArg.getName() + "}", "${" + urlArg.getName() + "}");
        }

        if(!endpoint.getUrlArgs().isEmpty()) {
            return "`" + url + "`";
        } else {
            return "'" + url + "'";
        }
    }


    private String getFnParams(List<Field> fields) {
        List<String> params = new ArrayList<>();
        fields.forEach(field ->
                params.add(field.getName() + (field.isRequired() ? "" : "?") + ": " + TypeWriter.printType(field.getType(), context))
        );

        return String.join(", ", params);
    }

    private String printParams(List<Field> fields) {
        String s = "{";
        s += fields.stream().map(field -> {
            if(field.getType() instanceof ObjectType) {
                return "..." + field.getName();
            } else {
                return field.getName();
            }
        }).collect(Collectors.joining(", "));
        s += "}";
        return s;
    }

    private String getCollectionName(Type type) {
        if(type instanceof ArrayType arr) {
            return getCollectionName(arr.getSubType());
        }

        if(type instanceof NamedType named) {
            return named.getName();
        }

        return "";
    }
}

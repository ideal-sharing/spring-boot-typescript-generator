package backend.spring;

import backend.EndPointParser;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import lombok.SneakyThrows;
import model.TypeContext;
import model.Endpoint;
import backend.TypeParser;
import model.types.*;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SpringEndpointParser implements EndPointParser {
    private final TypeParser typeParser;

    private final TypeContext context;

    private static final Set<String> IGONRED_ENDPOINT_PARAMS = Set.of(
            "org.springframework.web.server.ServerWebExchange",
            "org.springframework.web.server.WebSession",
            "jakarta.servlet.http.HttpServletRequest",
            "jakarta.servlet.http.HttpServletResponse"
    );

    public SpringEndpointParser(TypeContext context) {
        this.typeParser = new SpringTypeParser(context);
        this.context = context;
    }

    @Override
    @SneakyThrows
    public List<Endpoint> parseClass(CtClass clazz) {
        RestController annotation = (RestController) clazz.getAnnotation(RestController.class);
        RequestMapping requestMapping = (RequestMapping) clazz.getAnnotation(RequestMapping.class);
        if(annotation == null)  {
            return new ArrayList<>();
        }
        List<Endpoint> list = new ArrayList<>();
        if(requestMapping != null && requestMapping.value().length > 0) {
            for(String prefix: requestMapping.value()) {
                for (CtMethod method : clazz.getMethods()) {
                    parseMethod(method, list, prefix);
                }
            }
        } else {
            for (CtMethod method : clazz.getMethods()) {
                parseMethod(method, list, "");
            }
        }

        return list;
    }

    @SneakyThrows
    public void parseMethod(CtMethod method, List<Endpoint> endpoints, String prefix) {
        GetMapping getMapping = (GetMapping) method.getAnnotation(GetMapping.class);
        PostMapping postMapping = (PostMapping) method.getAnnotation(PostMapping.class);
        PutMapping putMapping = (PutMapping) method.getAnnotation(PutMapping.class);
        PatchMapping patchMapping = (PatchMapping) method.getAnnotation(PatchMapping.class);
        DeleteMapping deleteMapping = (DeleteMapping) method.getAnnotation(DeleteMapping.class);

        String className = method.getDeclaringClass().getSimpleName();

        if(getMapping != null) {
            for (String path : getMapping.value()) {
                Endpoint endpoint = new Endpoint(className, method.getName(), prefix + path, HttpMethod.GET, typeParser.parseType(method));
                parseArgs(method, endpoint);
                endpoints.add(endpoint);
            }
        }

        if(postMapping != null) {
            for (String path : postMapping.value()) {
                Endpoint endpoint = new Endpoint(className, method.getName(), prefix + path, HttpMethod.POST, typeParser.parseType(method));
                parseArgs(method, endpoint);
                endpoints.add(endpoint);
            }
        }

        if(putMapping != null) {
            for (String path : putMapping.value()) {
                Endpoint endpoint = new Endpoint(className, method.getName(), prefix + path, HttpMethod.PUT, typeParser.parseType(method));
                parseArgs(method, endpoint);
                endpoints.add(endpoint);
            }
        }

        if(patchMapping != null) {
            for (String path : patchMapping.value()) {
                Endpoint endpoint = new Endpoint(className, method.getName(), prefix + path, HttpMethod.PATCH, typeParser.parseType(method));
                parseArgs(method, endpoint);
                endpoints.add(endpoint);
            }
        }

        if(deleteMapping != null) {
            for (String path : deleteMapping.value()) {
                Endpoint endpoint = new Endpoint(className, method.getName(), prefix + path, HttpMethod.DELETE, typeParser.parseType(method));
                parseArgs(method, endpoint);
                endpoints.add(endpoint);
            }
        }
    }

    @SneakyThrows
    public void parseArgs(CtMethod method, Endpoint endpoint) {
        String signature = method.getGenericSignature() == null ? method.getSignature() : method.getGenericSignature();
        String args = signature.replaceFirst("\\).*$", ")").replace(")", "").replace("(", "");
        String returnTypeStr = signature.replaceFirst("\\(.*\\)", "");
        GenericTypeStringParser argsParser = new GenericTypeStringParser(args, context);
        GenericTypeStringParser returnParser = new GenericTypeStringParser(returnTypeStr, context);
        List<Intermediate> argTypes = argsParser.parseGenericArgs();
        Intermediate returnType = returnParser.parseGenericArgs().get(0);
        endpoint.setReturnType(typeParser.parseType(returnType));

        MethodInfo methodInfo = method.getMethodInfo();
        LocalVariableAttribute table = (LocalVariableAttribute) methodInfo.getCodeAttribute().getAttribute(javassist.bytecode.LocalVariableAttribute.tag);

        for (int i = 0; i < method.getParameterAnnotations().length; i++) {
            int nameIndex = table.nameIndex(i + 1);
            String variableName = methodInfo.getConstPool().getUtf8Info(nameIndex);

            if(method.getParameterAnnotations()[i].length == 0 && !IGONRED_ENDPOINT_PARAMS.contains(method.getParameterTypes()[i].getName())){
                endpoint.getParams().add(new Field(variableName, typeParser.parseType(argTypes.get(i))));
            }

            for(Object annotation: method.getParameterAnnotations()[i]) {
                if(annotation instanceof RequestParam requestParam) {
                    Field field = new Field(variableName, typeParser.parseType(argTypes.get(i)));
                    field.setRequired(requestParam.required());
                    endpoint.getParams().add(field);
                } else if(annotation instanceof PathVariable) {
                    endpoint.getUrlArgs().add(new Field(variableName, typeParser.parseType(argTypes.get(i))));
                } else if(annotation instanceof RequestBody) {
                    endpoint.setBody(typeParser.parseType(argTypes.get(i)));
                    setNeedsValidation(endpoint.getBody());
                }
            }
        }
    }

    private void setNeedsValidation(Type type) {

        if(type instanceof ObjectType o) {
            o.setNeedsValidation(true);
            o.getFields().forEach(field -> setNeedsValidation(field.getType()));
        }

        if(type instanceof EnumType e) {
            e.setNeedsValidation(true);
        }

        if(type instanceof ArrayType arr) {
            setNeedsValidation(arr.getSubType());
        }

        if(type instanceof MapType map) {
            setNeedsValidation(map.getKeySubType());
            setNeedsValidation(map.getValueSubType());
        }
    }
}


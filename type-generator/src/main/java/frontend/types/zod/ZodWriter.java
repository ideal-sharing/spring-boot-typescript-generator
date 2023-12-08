package frontend.types.zod;

import frontend.TypeScriptFile;
import frontend.types.TypeWriter;
import frontend.types.typescript.TypeScriptWriter;
import lombok.RequiredArgsConstructor;
import model.TypeContext;
import model.types.*;
import model.validation.Arrays;
import model.validation.Numbers;
import model.validation.Strings;
import model.validation.Validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
public class ZodWriter implements TypeWriter {
    private final String basePath;

    private final TypeScriptFile.Import zodImport = new TypeScriptFile.Import("zod", null, Set.of("z"));

    public List<TypeScriptFile> printAllTypes(TypeContext context) {
        List<TypeScriptFile> files = new ArrayList<>();
        context.getNamedObjects().forEach((name, namedType) -> {
            if(namedType.needsValidation() || namedType instanceof EnumType) {
                TypeScriptFile typeScriptFile = new TypeScriptFile();
                typeScriptFile.setLocation(basePath + TYPE_DECLARATIONS_DIR + "/" + name);
                typeScriptFile.setBody(printNamedType(name, namedType));
                files.add(typeScriptFile);
                context.getNamedObjectFiles().put(name, typeScriptFile);
            }

        });

        TypeScriptWriter typeScriptWriter = new TypeScriptWriter(basePath);
        files.addAll(typeScriptWriter.printAllNonValidatedTypes(context));

        // resolving imports:
        context.getNamedObjects().forEach((name, namedType) -> {
            if(namedType.needsValidation() || namedType instanceof EnumType) {
                TypeScriptFile file = context.getNamedObjectFiles().get(name);
                file.getImports().add(zodImport);
                if (namedType instanceof ObjectType objectType) {
                    objectType.getFields().forEach(field -> addZodModelImport(field.getType(), context, file));
                }
            }
        });

        return files;
    }

    private String printNamedType(String name, NamedType t) {
        StringBuilder body = new StringBuilder();
        if(t instanceof ObjectType o) {
            body.append("export const ").append(name).append("Model").append(" = z.object({\n");
            o.getFields().forEach(field -> {
                body.append("  ").append(field.getName()).append(": ").append(printZodType(field.getType()));
                field.getValidations().forEach(validation -> body.append(printValidation(validation)));
                if(!field.isRequired()) {
                    body.append(".optional().nullable()");
                }
                body.append(",\n");
            });
            body.append("});\n\n");

            body.append("type ").append(name).append(" = z.infer<typeof ").append(name).append("Model>;\n");
            body.append("export default ").append(name).append(";\n");
        } else if (t instanceof EnumType e) {
            body.append("export const ").append(name).append("Model = z.enum([\n");
            e.getValues().forEach(s ->
                    body.append("'").append(s).append("',\n")
            );
            body.append("]);\n\n");

            body.append("type ").append(name).append(" = z.infer<typeof ").append(name).append("Model>;\n");
            body.append("export default ").append(name).append(";\n");
        }
        return body.toString();
    }

    private String printValidation(Validation validation) {
        if(validation instanceof Strings.Email) {
            return ".email({ message: \"" + validation.message() + "\"})";
        }

        if(validation instanceof Strings.Regex re) {
            return ".regex(" + re.regex() + ", { message: \"" + validation.message() + "\"})";
        }

        if(validation instanceof Numbers.MinValue min) {
            return ".min(" + min.minValue() + ", { message: \"" + validation.message() + "\"})";
        }

        if(validation instanceof Numbers.MaxValue max) {
            return ".max(" + max.maxValue() + ", { message: \"" + validation.message() + "\"})";
        }

        if(validation instanceof Arrays.MinLength min) {
            return ".min(" + min.minLength() + ", { message: \"" + validation.message() + "\"})";
        }

        if(validation instanceof Arrays.MaxLength max) {
            return ".max(" + max.maxLength() + ", { message: \"" + validation.message() + "\"})";
        }

        throw new UnsupportedOperationException("Validation " + validation.getClass().getName() + " not supported in ZodWriter");
    }

    private String printZodType(Type t) {
        if(t instanceof NamedType o) {
            return o.getName() + "Model";
        }

        if(t instanceof ArrayType arr) {
            return printZodType(arr.getSubType()) + ".array()";
        }

        if(t instanceof MapType map) {
            return "z.record(" + printZodType(map.getKeySubType()) + ", " + printZodType(map.getValueSubType()) + ")";
        }

        if(t instanceof PrimitiveType p) {
            return switch (p) {
                case Int -> "z.number().int()";
                case Double -> "z.number()";
                case String, Date -> "z.string()";
                case Boolean -> "z.boolean()";
                default -> throw new UnsupportedOperationException("Unexpected zod type " + p);
            };
        }

        throw new UnsupportedOperationException("Unsupported Type: " + t.getClass().getName());
    }

    public void addZodModelImport(Type t, TypeContext context, TypeScriptFile file) {
        if(t instanceof NamedType o) {
            this.addZodModelImport(o, context, file);
        }

        if(t instanceof ArrayType arr ) {
            addZodModelImport(arr.getSubType(), context, file);
        }

        if(t instanceof MapType m) {
            addZodModelImport(m.getKeySubType(), context, file);
            addZodModelImport(m.getValueSubType(), context, file);
        }
    }

    public void addZodModelImport(NamedType o, TypeContext context, TypeScriptFile file) {
        TypeScriptFile toImport = context.getNamedObjectFiles().get(o.getName());

        if(toImport != file) {
            String location = file.getImportLocationFor(toImport);

            Optional<TypeScriptFile.Import> importOptional = file.getImports()
                    .stream()
                    .filter(imp -> imp.getLocation().equals(location))
                    .findFirst();

            String name = o.getName() + "Model";

            if (importOptional.isPresent()) {
                importOptional.get().getImports().add(name);
            } else {
                TypeScriptFile.Import newImport = new TypeScriptFile.Import();
                newImport.getImports().add(name);
                newImport.setLocation(location);
                file.getImports().add(newImport);
            }
        }
    }
}

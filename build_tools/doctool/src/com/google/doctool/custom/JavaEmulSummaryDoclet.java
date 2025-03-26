/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.doctool.custom;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.tools.Diagnostic;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A doclet for listing the specified classes and
 * their methods and constructors.
 */
public class JavaEmulSummaryDoclet implements Doclet {

    public static final String OPT_OUTFILE = "-outfile";
    private static final String JAVADOC_URL = "https://docs.oracle.com/en/java/javase/11/docs/api/";

    private Reporter reporter;
    private String outputFile;

    private static final String WONTFIX = "\uD83D\uDED1";
    private static final String REFLECTION = "\uD83D\uDD0D";
    private static final String LOCALES = "\uD83C\uDF10";
    private static final String UNDECIDED = "\u23F3";
    private Properties properties = new Properties();

    @Override
    public boolean run(DocletEnvironment env) {
        try {
            properties.load(Files.newInputStream(Path.of(
                "build_tools/doctool/src/com/google/doctool/custom/missing.properties")));
            System.out.println(properties.keySet());
            File outFile = new File(outputFile);
            outFile.getParentFile().mkdirs();
            try (FileWriter fw = new FileWriter(outFile, StandardCharsets.UTF_8);
                 PrintWriter pw = new PrintWriter(fw, true)) {

                pw.println("<ol class=\"toc\" id=\"pageToc\">");
                getSpecifiedPackages(env)
                        .forEach(pack -> {
                            pw.format("  <li><a href=\"#Package_%s\">%s</a></li>\n",
                                    pack.getQualifiedName()
                                            .toString().replace('.', '_'),
                                    pack.getQualifiedName().toString());
                        });

                pw.println("</ol>\n");
                Set<String> allClasses = getSpecifiedPackages(env)
                    .flatMap(pack -> pack.getEnclosedElements().stream()
                    .flatMap(clazz -> withInnerClasses(clazz, pack)))
                    .collect(Collectors.toSet());
                getSpecifiedPackages(env).forEach(pack -> {
                    Optional<Module> matchingModuleName = ModuleLayer.boot().modules().stream()
                            .filter(m -> m.getPackages().contains(pack.getQualifiedName().toString()))
                            .findFirst();

                    pw.format("<h2 id=\"Package_%s\">Package %s</h2>\n",
                            pack.getQualifiedName().toString().replace('.', '_'),
                            pack.getQualifiedName().toString());
                    pw.println("<dl>");

                    String packURL = JAVADOC_URL
                            + matchingModuleName.map(m -> m.getName() + "/").orElse("")
                            + pack.getQualifiedName().toString().replace(".", "/") + "/";

                    Iterator<? extends Element> classesIterator = pack.getEnclosedElements()
                            .stream()
                            .filter(element -> env.isSelected(element) && env.isIncluded(element))
                            .filter(element -> element.getModifiers().contains(Modifier.PUBLIC))
                            .sorted(Comparator.comparing((Element o) -> o.getSimpleName()
                                    .toString()))
                            .iterator();

                    while (classesIterator.hasNext()) {
                        Element cls = classesIterator.next();
                        // Each class links to Oracle's main JavaDoc
                        emitClassDocs(env, pw, packURL, cls, pack.getQualifiedName().toString() + ".", allClasses);
                        if (classesIterator.hasNext()) {
                            pw.print("\n");
                        }
                    }

                    pw.println("</dl>\n");
                });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    private Stream<String> withInnerClasses(Element clazz, PackageElement pack) {
        return Stream.concat(
            Stream.of(pack.getQualifiedName() + "." + clazz.getSimpleName().toString()),
            clazz.getEnclosedElements().stream()
                .map(inner -> pack.getQualifiedName()
                    + "." + clazz.getSimpleName() + "$" + inner.getSimpleName()));
    }

    private void emitClassDocs(DocletEnvironment env, PrintWriter pw, String packURL, Element cls,
                               String pack, Set<String> allClasses) {
        pw.format("  <dt><a href=\"%s%s.html\">%s</a></dt>\n", packURL,
                qualifiedSimpleName(cls), qualifiedSimpleName(cls));

        // Print out all fields
        String fields = cls.getEnclosedElements()
                .stream()
                .filter(element -> element.getKind().isField())
                .filter(field -> field.getModifiers().contains(Modifier.PUBLIC))
                .map(field -> field.getSimpleName().toString())
                .collect(Collectors.joining(", "));

        if (!fields.isEmpty()) {
            pw.format("  <dd style='margin-bottom: 0.5em;'><strong>Fields:</strong> %s</dd>\n",
                fields);
        }

        List<String> constructors = cls.getEnclosedElements()
                .stream()
                .filter(element -> ElementKind.CONSTRUCTOR == element.getKind())
                .filter(member -> member.getModifiers().contains(Modifier.PUBLIC))
                .map(member -> (ExecutableElement) member)
                .map(executableElement -> flatSignature(t -> simpleParamName(env, t), cls, executableElement))
                .collect(Collectors.toList());

        List<String> methods = getMethodNames(cls, t -> simpleParamName(env, t));

        List<String> erasedMethods = getMethodNames(cls, t -> erasedParamName(env, t));

        if (!constructors.isEmpty()) {
            pw.format("  <dd><strong>Constructors:</strong> %s</dd>\n",
                createMemberList(constructors));
        }
        // Print out all constructors and methods
        if (!methods.isEmpty()) {
            pw.format("  <dd><strong>Methods:</strong> %s</dd>\n",
                createMemberList(methods));
        }
        String[] parts = (pack + cls.getSimpleName()).split("\\$");
        List<String> missingMethods = new ArrayList<>();
        try {
            Class<?> c = Class.forName(parts[0]);
            if (parts.length > 1) {
                c = Arrays.stream(c.getDeclaredClasses())
                    .filter(inner -> inner.getSimpleName().equals(cls.getSimpleName().toString()))
                    .findFirst().orElse(c);
            }
            Class<?> superclass = c.getSuperclass() != null ? c.getSuperclass() : Object.class;
            List<Method> superMethods = new ArrayList<>(Arrays.asList(superclass.getMethods()));

            for (Class<?> iface: c.getInterfaces()) {
                if (!allClasses.contains(iface.getTypeName())) {
                    //System.out.println("Missing interface: " + iface.getTypeName());
                }
                superMethods.addAll(Arrays.asList(iface.getMethods()));
            }
            for (Method method: c.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isPublic(method.getModifiers())
                        && !erasedMethods.contains(getReflectionSignature(method))
                        && superMethods.stream().noneMatch(m ->
                    nameAndParamCount(m).equals(nameAndParamCount(method)))) {
                    String status = getStatus(parts[0] + "#" + getReflectionSignature(method));
                    missingMethods.add("<span class=\"status-" + status + "\">[" + status
                        + "]</span>" + getReflectionSignature(method));
                    //System.out.println(parts[0] + "#" + getReflectionSignature(method) + "\\");
                }
            }
        } catch (ClassNotFoundException e) {
           // System.out.println("Loading failed " + parts[0]);
          // OK to ignore
        }
        if (!missingMethods.isEmpty()) {
            pw.format("  <dd><strong>Missing implementation:</strong> %s</dd>\n", createMemberList(missingMethods));
        }
        Iterator<? extends Element> classesIterator = cls.getEnclosedElements()
                .stream()
                .filter(element -> element.getKind().isClass()
                        || element.getKind().isInterface()
                        || ElementKind.ENUM == element.getKind())
                .filter(element -> element.getModifiers().contains(Modifier.PUBLIC))
                .sorted(Comparator.comparing((Element o) -> o.getSimpleName().toString()))
                .iterator();
        if (classesIterator.hasNext()) {
            pw.print("\n");
        }
        while (classesIterator.hasNext()) {
            Element innerCls = classesIterator.next();
            // Each class links to Sun's main JavaDoc
            emitClassDocs(env, pw, packURL, innerCls, pack + cls.getSimpleName() + "$", allClasses);
            if (classesIterator.hasNext()) {
                pw.print("\n");
            }
        }
    }

    private String getStatus(String methodRef) {
        for (Object category: properties.keySet()) {
            if (properties.get(category).toString().contains(methodRef)) {
                return category.toString();
            }
        }
        return "?";
    }

    private List<String> getMethodNames(Element cls, Function<TypeMirror, String> typeNamer) {
        return cls.getEnclosedElements()
            .stream()
            .filter(element -> ElementKind.METHOD == element.getKind())
            .filter(member -> member.getModifiers().contains(Modifier.PUBLIC))
            .map(member -> (ExecutableElement) member)
            .map(executableElement -> flatSignature(typeNamer, cls, executableElement))
            .collect(Collectors.toList());
    }

    private String nameAndParamCount(Method m) {
        return m.getName() + ":" + m.getParameterCount();
    }

    private String getReflectionSignature(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameters())
            .map(param -> param.getType().getSimpleName())
            .collect(Collectors.joining(", ")) + ")";
    }

    private String createMemberList(Collection<String> members) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> iter = members.iterator();
        while (iter.hasNext()) {
            String member = iter.next();
            sb.append(member);
            if (iter.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private String qualifiedSimpleName(Element element) {
        String elementName = element.getSimpleName().toString();
        if (ElementKind.PACKAGE != element.getEnclosingElement().getKind()) {
            return qualifiedSimpleName(element.getEnclosingElement()) + "." + elementName;
        }
        return elementName;
    }

    private String flatSignature(Function<TypeMirror, String> namer, Element parent, ExecutableElement member) {
        return (ElementKind.CONSTRUCTOR == member.getKind()
                ? parent.getSimpleName().toString()
                : member.getSimpleName().toString()) +
                "(" + member.getParameters()
                .stream()
                .map(Element::asType)
                .map(namer)
                .collect(Collectors.joining(", ")) + ")";
    }

    private String simpleParamName(DocletEnvironment env, TypeMirror type) {
        if (type.getKind().isPrimitive() || TypeKind.TYPEVAR == type.getKind()) {
            return String.valueOf(type);
        } else if (TypeKind.ARRAY == type.getKind()) {
            return simpleParamName(env, ((ArrayType) type).getComponentType()) + "[]";
        } else {
            return qualifiedSimpleName(env.getTypeUtils().asElement(type));
        }
    }

    private String erasedParamName(DocletEnvironment env, TypeMirror type) {
        if (TypeKind.TYPEVAR == type.getKind()) {
            TypeMirror upperBound = ((TypeVariable) type).getUpperBound();
            return erasedParamName(env, upperBound);
        } else if (type.getKind().isPrimitive()) {
            return String.valueOf(type);
        } else if (TypeKind.ARRAY == type.getKind()) {
            return erasedParamName(env, ((ArrayType) type).getComponentType()) + "[]";
        } else {
            return env.getTypeUtils().asElement(type).getSimpleName().toString();
        }
    }

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return "JreEmulationSummaryDoclet";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        Option[] options = {
                new Option() {

                    @Override
                    public int getArgumentCount() {
                        return 1;
                    }

                    @Override
                    public String getDescription() {
                        return "JRE emulation summary Doc location";
                    }

                    @Override
                    public Kind getKind() {
                        return Kind.STANDARD;
                    }

                    @Override
                    public List<String> getNames() {
                        return List.of(OPT_OUTFILE);
                    }

                    @Override
                    public String getParameters() {
                        return "file";
                    }

                    @Override
                    public boolean process(String opt, List<String> arguments) {
                        if (arguments.isEmpty()) {
                            reporter.print(Diagnostic.Kind.ERROR,
                                    "You must specify an output filepath with "
                                            + OPT_OUTFILE);
                            return false;
                        }
                        reporter.print(Diagnostic.Kind.NOTE,
                                "JRE emulation summary Doclet Option : "
                                + arguments.get(0));
                        outputFile = arguments.get(0);
                        return true;
                    }
                }
        };
        return new HashSet<>(Arrays.asList(options));
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private Stream<PackageElement> getSpecifiedPackages(DocletEnvironment root) {
        return root.getSpecifiedElements()
                .stream()
                .filter(element -> ElementKind.PACKAGE == element.getKind())
                .map(element -> (PackageElement) element);
    }
}

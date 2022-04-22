import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedEnumConstantDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserVariableDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import sun.misc.Launcher;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;


public class ParseProject {
    public static void main(String[] args) throws IOException {

        //源代码目录
        Path projectRoot = Paths.get("G:\\kaifa_environment\\code\\java\\javaparser-findUsage\\src\\main\\java");


        //获取项目依赖的所有jar
        //方式一，手动配置，项目依赖的所有jar路径，以及源码路径，ReflectionTypeSolver是jre中的jar，所有只需要把第三方jar的路径填入即可,可以用maven把所有jar整合到一起
    /*    TypeSolver myTypeSolver = new CombinedTypeSolver(
                , new JarTypeSolver("G:\\kaifa_environment\\code\\java\\javaparser-findUsage\\target\\javaparser-findUsage-1.0-SNAPSHOT.jar")
                , new JavaParserTypeSolver(codeSourceRoot)
        );*/



        //方式二，获取项目依赖的所有jar
        ClassLoader classLoader = ParseProject.class.getClassLoader();
        URL[] urLs = ((URLClassLoader) classLoader).getURLs();
        ArrayList<TypeSolver> typeSolvers = new ArrayList<>();
        for(int i = 0; i < urLs.length; i++) {
            URL urL = urLs[i];
            String file = urL.getFile().substring(1);
            boolean isFile = new File(file).isFile();
            if (isFile) {
                JarTypeSolver jarTypeSolver = new JarTypeSolver(file);
                typeSolvers.add(jarTypeSolver);
            }else {
                JavaParserTypeSolver jarTypeSolver = new JavaParserTypeSolver(file);
                typeSolvers.add(jarTypeSolver);
            }
        }
        typeSolvers.add(new JavaParserTypeSolver(projectRoot));
        
        Map<String, Integer> accessMap = new HashMap();

        Path codeSourceRoot = projectRoot.resolve(projectRoot);
        System.out.println(codeSourceRoot);
        SourceRoot sourceRoot = new SourceRoot(codeSourceRoot);


        TypeSolver myTypeSolver = new CombinedTypeSolver(typeSolvers);

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(myTypeSolver);

        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setSymbolResolver(symbolSolver);


        try {
            sourceRoot.getParserConfiguration().setSymbolResolver(symbolSolver);
            List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse();
            for (ParseResult<CompilationUnit> parseResult : parseResults) {
                parseResult.ifSuccessful(cu -> {

                    List<String> methodCall = getMethodCall(cu);
                    //   System.out.println("方法：");
                    // System.out.println(methodCall);

                    List<String> fieldCall = getFieldCall(cu);
                    //     System.out.println("字段：");
                    //  System.out.println(fieldCall);

                    methodCall.addAll(fieldCall);

                    for (String qualifiedName : methodCall) {
                        Integer count = accessMap.getOrDefault(qualifiedName, Integer.valueOf(0));
                        accessMap.put(qualifiedName, count + 1);
                    }


                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(accessMap);


    }


    public static List<String> getMethodCall(CompilationUnit cu) {
        List<String> methodCallExprs = new ArrayList<>();
        cu.findAll(MethodCallExpr.class).forEach(mce -> {
            methodCallExprs.add(mce.resolve().getQualifiedSignature());
        });

        return methodCallExprs;
    }


    public static List<String> getFieldCall(CompilationUnit cu) {
        List<String> fieldAccess = new ArrayList<>();

        //获取字段访问情况
        cu.findAll(FieldAccessExpr.class).forEach(new Consumer<FieldAccessExpr>() {
            @Override
            public void accept(FieldAccessExpr mce) {
                try {
                    ResolvedValueDeclaration resolve = mce.resolve();
                    String fieldPath = getQualifiedName(resolve, mce.getNameAsString());
                    fieldAccess.add(fieldPath);
                } catch (Exception e) {
                }

            }
        });


        // 名称访问情况
        cu.findAll(NameExpr.class).forEach(mce -> {
            try {
                ResolvedValueDeclaration resolve = mce.resolve();
                //System.out.println(mce.getName());
                String fieldPath = getQualifiedName(resolve, mce.getNameAsString());
                if (fieldPath.length() > 0) {
                    fieldAccess.add(fieldPath);
                }

            } catch (Exception e) {
            }
        });
        return fieldAccess;
    }

    public static String getQualifiedName(ResolvedValueDeclaration resolve, String fieldName) {
        String fieldPath = "";

/*        if (resolve instanceof JavaParserFieldDeclaration) {
            JavaParserFieldDeclaration javaParserFieldDeclaration = (JavaParserFieldDeclaration) resolve;
            FieldDeclaration wrappedNode = javaParserFieldDeclaration.getWrappedNode();
            Node parentNode = wrappedNode.getParentNode().get();
            if (parentNode instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration typeDeclaration = (ClassOrInterfaceDeclaration) parentNode;
                //获取typeDeclaration的qualifiedName
                fieldPath = typeDeclaration.getFullyQualifiedName().get() + "." + fieldName;
            }
        } else if (resolve instanceof ReflectionFieldDeclaration) {
            ReflectionFieldDeclaration reflectionFieldDeclaration = (ReflectionFieldDeclaration) resolve;
            ResolvedTypeDeclaration resolvedTypeDeclaration = reflectionFieldDeclaration.declaringType();//获取字段的类型
            fieldPath = resolvedTypeDeclaration.getQualifiedName() + "." + fieldName;
        } else if (resolve instanceof JavassistFieldDeclaration){
            ResolvedFieldDeclaration javassistFieldDeclaration = (ResolvedFieldDeclaration) resolve;
            fieldPath = javassistFieldDeclaration.declaringType().getQualifiedName() + "." + fieldName;
        }*/


        if (resolve instanceof ResolvedFieldDeclaration) {
            ResolvedFieldDeclaration resolvedValueDeclaration = (ResolvedFieldDeclaration) resolve;
            fieldPath = resolvedValueDeclaration.declaringType().getQualifiedName() + "." + fieldName;
        } else if (resolve instanceof ResolvedEnumConstantDeclaration) {
            ResolvedType type = resolve.getType();
            String qualifiedName = ((ReferenceTypeImpl) type).getTypeDeclaration().get().getQualifiedName();
            fieldPath = qualifiedName + "." + fieldName;
        } else if (resolve instanceof JavaParserParameterDeclaration) {
            //System.out.println("函数入参:"+resolve.getName());
        } else if (resolve instanceof JavaParserVariableDeclaration) {
            //System.out.println("变量引用:"+resolve.getName());
        } else {
            System.out.println("没解析的类型");
        }
        return fieldPath;
    }
}

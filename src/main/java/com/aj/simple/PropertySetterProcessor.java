package com.aj.simple;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;
import java.util.regex.Pattern;

import com.sun.tools.javac.util.*;

@SupportedAnnotationTypes("com.aj.simple.PropertySetter")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PropertySetterProcessor extends AbstractProcessor {
    // 打印log
    private Messager messager;
    // 抽象语法树
    private JavacTrees trees;
    // 封装了创建AST节点的一些方法
    private TreeMaker treeMaker;
    // 提供了创建标识符的一些方法
    private Names names;

    // 初始化方法
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
//        messager.printMessage(Diagnostic.Kind.NOTE, SetterProcessor.class.getSimpleName() + " round " + (++round));

        for (TypeElement annotation : annotations) {
            roundEnv.getElementsAnnotatedWith(annotation).forEach(element -> {
                // 获取对应的语法树
                JCTree jcTree = trees.getTree(element);
                // 通过visitor模式，添加setter方法；如果为final字段，则不生成setter方法
                jcTree.accept(new TreeTranslator() {
                    @Override
                    public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                        super.visitClassDef(jcClassDecl);
                        // 1.获取非final字段
                        List<JCTree.JCVariableDecl> jcVariableDecls = List.nil();
                        for (JCTree item : jcClassDecl.defs) {
                            if (item.getKind() == Tree.Kind.VARIABLE) {
                                JCTree.JCVariableDecl jcVariableDecl = (JCTree.JCVariableDecl) item;
                                if (!jcVariableDecl.getModifiers().getFlags().contains((Modifier.FINAL))) {
                                    jcVariableDecls = jcVariableDecls.append(jcVariableDecl);
                                }
                            }
                        }
                        // 2.创建对应的setter方法
                        jcVariableDecls.forEach(jcVariableDecl -> {
                            // 创建对应的setter方法
                            JCTree.JCMethodDecl jcMethodDecl = generateSetterMethod(jcVariableDecl);
                            if (jcMethodDecl != null) {
                                // 更新类
                                jcClassDecl.defs = jcClassDecl.defs.append(jcMethodDecl);
                            }

                        });
                        // 3.更新jcClassDecl
                        this.result = jcClassDecl;
                    }
                });
            });
        }

        return roundEnv.processingOver();
    }

    /**
     * 通过treeMaker创建setter方法
     **/
    private JCTree.JCMethodDecl generateSetterMethod(JCTree.JCVariableDecl jcVariableDecl) {
        // 1.创建赋值语句, 构建方法体
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        JCTree.JCExpressionStatement statement = treeMaker.Exec(treeMaker.Assign(treeMaker.Select(treeMaker.Ident(names.fromString("this")), jcVariableDecl.getName()), treeMaker.Ident(jcVariableDecl.getName())));
        statements.append(statement);
        JCTree.JCBlock body = treeMaker.Block(0, statements.toList());

        // 2.生成方法参数之前，指明当前语法节点在语法树中的位置，避免出现异常 java.lang.AssertionError: Value of x -1
        treeMaker.pos = jcVariableDecl.pos;

        // 3.创建方法
        Name methodName = setterMethodName(jcVariableDecl.getName());

        if (methodName == null) return null;

//        messager.printMessage(Diagnostic.Kind.NOTE, jcVariableDecl.getName() + " has been processed");
        JCTree.JCVariableDecl param = treeMaker.VarDef(treeMaker.Modifiers(Flags.PARAMETER), jcVariableDecl.getName(), jcVariableDecl.vartype, null);
        // 通过这种方式定义入参，可能会出现NullPointer错误
        // JCTree.JCVariableDecl param = treeMaker.Param( jcVariableDecl.getName(), jcVariableDecl.vartype.type, jcVariableDecl.sym);
        // 两种定义void返回值的方法等价
        JCTree.JCExpression returnType = treeMaker.Type(new Type.JCVoidType());
        // JCTree.JCExpression returnType = treeMaker.TypeIdent(TypeTag.VOID);
        // 指定方法的修饰符、方法名、返回参数、泛型参数、入参、异常声明、方法体、defaultValue（null即可）
        JCTree.JCMethodDecl jcMethodDecl = treeMaker.MethodDef(treeMaker.Modifiers(Flags.PUBLIC), methodName, returnType, List.nil(), List.of(param), List.nil(), body, null);

        return jcMethodDecl;
    }

    /**
     * 创建方法名标识符
     **/
    private Name setterMethodName(Name variableName) {
        String name = variableName.toString();

        if ( name.matches(".*[A-Z].*")) {
            ///驼峰
            name = toUnderCase(name);
            return names.fromString("set" + name.substring(0, 1).toUpperCase() + name.substring(1));
        } else if (name.matches(".*[_].*")) {
            ///是下划线
            name = toCamelCase(name);
            return names.fromString("set" + name.substring(0, 1).toUpperCase() + name.substring(1));
        }

        return null;
    }



    private JCTree.JCExpression memberAccess(String components) {
        String[] componentArray = components.split("\\.");
        JCTree.JCExpression expr = treeMaker.Ident(getNameFromString(componentArray[0]));
        for (int i = 1; i < componentArray.length; i++) {
            expr = treeMaker.Select(expr, getNameFromString(componentArray[i]));
        }
        return expr;
    }

    private Name getNameFromString(String s) { return names.fromString(s); }

    private static final char UNDER_LINE = '_';

    /**
     * 下划线转驼峰
     *
     * @param name
     * @return
     */
    public static String toCamelCase(String name) {
        if (null == name || name.length() == 0) {
            return null;
        }

        int length = name.length();
        StringBuilder sb = new StringBuilder(length);
        boolean underLineNextChar = false;

        for (int i = 0; i < length; ++i) {
            char c = name.charAt(i);
            if (c == UNDER_LINE) {
                underLineNextChar = true;
            } else if (underLineNextChar) {
                sb.append(Character.toUpperCase(c));
                underLineNextChar = false;
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * 驼峰转下划线
     *
     * @param name
     * @return
     */
    public static String toUnderCase(String name) {
        if (name == null) {
            return null;
        }

        int len = name.length();
        StringBuilder res = new StringBuilder(len + 2);
        char pre = 0;
        for (int i = 0; i < len; i++) {
            char ch = name.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (pre != UNDER_LINE) {
                    res.append(UNDER_LINE);
                }
                res.append(Character.toLowerCase(ch));
            } else {
                res.append(ch);
            }
            pre = ch;
        }
        return res.toString();
    }



}

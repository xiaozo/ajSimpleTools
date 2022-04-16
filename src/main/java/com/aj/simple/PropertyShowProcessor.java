package com.aj.simple;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.Set;
@SupportedAnnotationTypes({"com.aj.simple.PropertyShow"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PropertyShowProcessor extends AbstractProcessor {

    private Messager messager;
    private JavacTrees trees;
    private TreeMaker treeMaker;
    private Names names;

    private Elements elementsUtils;


    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = com.sun.tools.javac.util.Names.instance(context);

        elementsUtils = processingEnv.getElementUtils();

    }

    public  JCTree.JCExpression chainDots(String... elems) {
        assert elems != null;

        JCTree.JCExpression e = null;
        for (int i = 0 ; i < elems.length ; i++) {
            e = e == null ? treeMaker.Ident(names.fromString(elems[i])) : treeMaker.Select(e, names.fromString(elems[i]));
        }
        assert e != null;

        return e;
    }
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(PropertyShow.class);

        set.forEach(element -> {
            JCTree jcTree = trees.getTree(element);
            TypeElement enClosingElement = (TypeElement)element.getEnclosingElement();
            JCTree.JCClassDecl jcClassDecl = (JCTree.JCClassDecl)trees.getTree(enClosingElement);

            jcTree.accept(new TreeTranslator() {
                @Override
                public void visitVarDef(JCVariableDecl var1) {

                    if (var1.getKind().equals(Tree.Kind.VARIABLE)) {
                        boolean enable = element.getAnnotation(PropertyShow.class).enable();
//                        if (enable == true) {
                            //添加方法属性
                            String suffix = element.getAnnotation(PropertyShow.class).suffix();
                            String fieldName = var1.getName().toString();
                            fieldName = fieldName + suffix;
                            JCVariableDecl val = treeMaker.VarDef(treeMaker.Modifiers(Flags.PUBLIC),names.fromString(fieldName),memberAccess("java.lang.String"),null);
                            if (enable == false) {
                                JCTree.JCExpression expression = memberAccess("com.aj.simple.PropertyShowIgnore");
                                JCTree.JCAnnotation jcAnnotation = treeMaker.Annotation(expression, List.nil());
                                val.mods.annotations = val.mods.getAnnotations().prepend(jcAnnotation);

//                                try {
//                                    if (Class.forName("com.fasterxml.jackson.annotation.JsonIgnore") != null) {
//                                        expression = memberAccess("com.fasterxml.jackson.annotation.JsonIgnore");
//                                        jcAnnotation = treeMaker.Annotation(expression, List.nil());
//                                        val.mods.annotations = val.mods.getAnnotations().prepend(jcAnnotation);
//                                    }
//
//                                } catch (ClassNotFoundException e) {
//
//                                    e.printStackTrace();
//                                }


                            }


                        jcClassDecl.defs = jcClassDecl.defs.prepend(val);


                            //增加方法
                            String obj = element.getAnnotation(PropertyShow.class).obj();
                            String meth = element.getAnnotation(PropertyShow.class).meth();

                            ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
                            if (enable) {
                                ///通过变量访问
//                                JCTree.JCExpression param = treeMaker.Select(treeMaker.Ident(names.fromString("this")), var1.getName());
                                ///通过get方法访问
                                JCTree.JCExpression param = treeMaker.Apply(List.of(var1.vartype),memberAccess("this."+getNewMethodName( var1.getName().toString())),List.nil());
                                statements.append(treeMaker.Return( treeMaker.Apply(List.of(var1.vartype),memberAccess(obj+"."+meth),List.of(param))));
                            } else  {
                                statements.append(treeMaker.Return(treeMaker.Literal(TypeTag.BOT, null)));
                            }

                            JCTree.JCBlock body = treeMaker.Block(0, statements.toList());
                            //get
                            JCTree.JCMethodDecl newGetMethodDecl = treeMaker.MethodDef(treeMaker.Modifiers(Flags.PUBLIC), getNewMethodName(fieldName), memberAccess("java.lang.String"), List.nil(), List.nil(), List.nil(), body, null);
                            jcClassDecl.defs = jcClassDecl.defs.prepend(newGetMethodDecl);
                        }

//                    }
                    super.visitVarDef(var1);


                }

            });
        });



        return true;
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

    private String getClassName(TypeElement enClosingElement, String packageName) {
        int packageLength = packageName.length()+1;
        return enClosingElement.getQualifiedName().toString().substring(packageLength).replace(".","$");
    }

    private String getPackageName(TypeElement enClosingElement) {
        return elementsUtils.getPackageOf(enClosingElement).getQualifiedName().toString();
    }

    private Name getNewMethodName( String s) {
        return names.fromString("get" + s.substring(0, 1).toUpperCase() + s.substring(1, s.length()));
    }



}

package jdk.tools.jlink.internal.plugins;

import jdk.internal.org.objectweb.asm.tree.ClassNode;

import java.util.*;

public class HierarchyContext {

    private boolean closureComputed;
    private Map<String, List<String>> superMap;
    private Map<String, Set<String>> subMap;
    private Map<String, List<String>> superInfMap;
    private List<ClassNode> classNodes;

    public HierarchyContext() {
        superMap = new HashMap<>();
        subMap = new HashMap<>();
        superInfMap = new HashMap<>();
        classNodes = new ArrayList<>();
    }

    public void addSuperClass(String clazz, String superClass) {
        assert !closureComputed : "Cannot alter the hierarchy after computing the closure of data";
        List<String> superClasses = new ArrayList<>();
        if (superClass != null) {
            superClasses.add(superClass);
        }
        superMap.put(clazz, superClasses);
    }

    public void addSuperInterface(String clazz, String superInf) {
        assert !closureComputed : "Cannot alter the hierarchy after computing the closure of data";
        List<String> superInfs = new ArrayList<>();
        if (superInf != null) {
            superInfs.add(superInf);
        }
        superInfMap.put(clazz, superInfs);
    }

    public void addClassNode(ClassNode cn) {
        assert !closureComputed : "Cannot add class nodes after computing the closure of data";
        classNodes.add(cn);
    }

    public ClassNode getClassNode(String className) {
        return classNodes.stream().
                filter(cn -> cn.name.equals(className))
                .findFirst()
                .orElse(null);
    }

    public List<String> getSuperClasses(String clazz) {
        if (superMap.containsKey(clazz)) {
            return superMap.get(clazz);
        }
        return java.util.Collections.emptyList();
    }

    public Set<String> getSubclasses(String clazz) {
        assert closureComputed: "Cannot call for hierarchy information before closure computation";
        if (subMap.containsKey(clazz)) {
            return subMap.get(clazz);
        }
        return null;
    }

    public List<String> getSuperInterfaces(String clazz) {
        if (superInfMap.containsKey(clazz)) {
            return superInfMap.get(clazz);
        }
        return java.util.Collections.emptyList();
    }

    public void computeClosure() {
        assert !closureComputed: "Closure can only be computed once";

        // construct closure of superclasses
        for (String c : superMap.keySet()) {
            List<String> itr = superMap.get(c);
            if (itr != null && itr.size() > 0) {
                for (itr = superMap.get(itr.get(0)); itr != null && itr.size() > 0; itr = superMap.get(itr.get(0))) {
                    superMap.get(c).addAll(itr);
                    if (itr.size() > 1)
                        break;
                }
            }
        }
        for (String c : superMap.keySet()) {
            for (String s : superMap.get(c)) {
                if (!subMap.containsKey(s)) {
                    subMap.put(s, new HashSet<>());
                }
                subMap.get(s).add(c);
            }
        }

        for (String c : superInfMap.keySet()) {
            List<String> itr = superInfMap.get(c);
            if (itr != null && itr.size() > 0) {
                for (itr = superInfMap.get(itr.get(0)); itr != null && itr.size() > 0; itr = superInfMap.get(itr.get(0))) {
                    superInfMap.get(c).addAll(itr);
                    if (itr.size() > 1)
                        break;
                }
            }
        }

        closureComputed = true;
    }
}

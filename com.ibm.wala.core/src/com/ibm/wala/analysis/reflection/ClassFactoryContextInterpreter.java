/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.analysis.reflection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.cfg.InducedCFG;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.summaries.SyntheticIR;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.IRView;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.EmptyIterator;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.NonNullSingletonIterator;

/**
 * An {@link SSAContextInterpreter} specialized to interpret reflective class factories (e.g. Class.forName()) in a
 * {@link JavaTypeContext} which represents the point-type of the class object created by the call.
 */
public class ClassFactoryContextInterpreter implements SSAContextInterpreter {

  private static final boolean DEBUG = false;

/** BEGIN Custom change: caching */
  private final Map<String, IR> cache = HashMapFactory.make();
  
/** END Custom change: caching */
  @Override
  public IR getIR(CGNode node) {
    if (node == null) {
      throw new IllegalArgumentException("node is null");
    }
    assert understands(node);
    if (DEBUG) {
      System.err.println("generating IR for " + node);
    }
/** BEGIN Custom change: caching */
    
    
    final JavaTypeContext context = (JavaTypeContext) node.getContext();
    final IMethod method = node.getMethod();
    final String hashKey = method.toString() + "@" + context.toString();
    
    IR result = cache.get(hashKey);
    
    if (result == null) {
      result = makeIR(method, context);
      cache.put(hashKey, result);
    }
    
/** END Custom change: caching */
    return result;
  }

  @Override
  public IRView getIRView(CGNode node) {
    return getIR(node);
  }

  @Override
  public int getNumberOfStatements(CGNode node) {
    assert understands(node);
    return getIR(node).getInstructions().length;
  }

  /*
   * @see com.ibm.wala.ipa.callgraph.propagation.rta.RTAContextInterpreter#understands(com.ibm.wala.ipa.callgraph.CGNode)
   */
  @Override
  public boolean understands(CGNode node) {
    if (node == null) {
      throw new IllegalArgumentException("node is null");
    }
    if (!(node.getContext() instanceof JavaTypeContext)) {
      return false;
    }
    return ClassFactoryContextSelector.isClassFactory(node.getMethod().getReference());
  }

  /*
   * @see com.ibm.wala.ipa.callgraph.propagation.rta.RTAContextInterpreter#iterateNewSites(com.ibm.wala.ipa.callgraph.CGNode)
   */
  @Override
  public Iterator<NewSiteReference> iterateNewSites(CGNode node) {
    if (node == null) {
      throw new IllegalArgumentException("node is null");
    }
    assert understands(node);
    JavaTypeContext context = (JavaTypeContext) node.getContext();
    TypeReference tr = context.getType().getTypeReference();
    if (tr != null) {
      return new NonNullSingletonIterator<NewSiteReference>(NewSiteReference.make(0, tr));
    }
    return EmptyIterator.instance();
  }

  /*
   * @see com.ibm.wala.ipa.callgraph.propagation.rta.RTAContextInterpreter#iterateCallSites(com.ibm.wala.ipa.callgraph.CGNode)
   */
  @Override
  public Iterator<CallSiteReference> iterateCallSites(CGNode node) {
    assert understands(node);
    return EmptyIterator.instance();
  }

  private static SSAInstruction[] makeStatements(JavaTypeContext context) {
    SSAInstructionFactory insts = context.getType().getType().getClassLoader().getInstructionFactory();
    ArrayList<SSAInstruction> statements = new ArrayList<SSAInstruction>();
    // vn1 is the string parameter
    int retValue = 2;
    TypeReference tr = context.getType().getTypeReference();
    if (tr != null) {
      SSALoadMetadataInstruction l = insts.LoadMetadataInstruction(statements.size(), retValue, TypeReference.JavaLangClass, tr);
      statements.add(l);
      SSAReturnInstruction R = insts.ReturnInstruction(statements.size(), retValue, false);
      statements.add(R);
    } else {
      SSAThrowInstruction t = insts.ThrowInstruction(statements.size(), retValue);
      statements.add(t);
    }
    SSAInstruction[] result = new SSAInstruction[statements.size()];
    statements.toArray(result);
    return result;
  }

  private static IR makeIR(IMethod method, JavaTypeContext context) {
    SSAInstruction instrs[] = makeStatements(context);
    return new SyntheticIR(method, context, new InducedCFG(instrs, method, context), instrs, SSAOptions.defaultOptions(), null);
  }

  @Override
  public boolean recordFactoryType(CGNode node, IClass klass) {
    return false;
  }

  @Override
  public Iterator<FieldReference> iterateFieldsRead(CGNode node) {
    return EmptyIterator.instance();
  }

  @Override
  public Iterator<FieldReference> iterateFieldsWritten(CGNode node) {
    return EmptyIterator.instance();
  }

  @Override
  public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCFG(CGNode N) {
    return getIR(N).getControlFlowGraph();
  }

  @Override
  public DefUse getDU(CGNode node) {
    return new DefUse(getIR(node));
  }
}

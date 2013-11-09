/* Copyright (c) 2013 Michael Heilmann */
package com.ibm.wala.analysis.reflection.ext;

import java.util.Collection;

import com.ibm.wala.analysis.reflection.JavaLangClassContextInterpreter;
import com.ibm.wala.analysis.typeInference.PointType;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.ConstantKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.intset.EmptyIntSet;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;

/**
 * @brief
 *  Produces {@link com.ibm.wala.analysis.reflection.ext.GetMethodContext} if the following is true:
 *  - The method to be interpreted is either
 *    {@link java.lang.Class#getMethod(String, Class...)} or
 *    {@link java.lang.Class#getDeclaredMethod(String, Class...)}.
 *  - The type of the "this" argument is known.
 *  - The value of the first argument (the method name) is a constant.
 * @author
 *  Michael Heilmann
 */
public class GetMethodContextSelector implements ContextSelector {
  
  /**
   * @brief
   *  If @a true, debug information is emitted.
   */
  protected static final boolean DEBUG = false;

  /**
   * @brief 
   *  If
   *  - the {@link CallSiteReference} invokes either {@link java.lang.Class#getMethod}
   *    or {@link java.lang.Class#getDeclaredMethod}, 
   *  - and the receiver is a type constant and
   *  - the first argument is a constant,
   *  then return a {@link GetMethodContextSelector}.
   */
  @Override
  public Context getCalleeTarget(CGNode caller,CallSiteReference site,IMethod callee,InstanceKey[] receiver) {
    if (receiver != null && receiver.length > 0 && mayUnderstand(caller, site, callee, receiver[0])) {
      if (DEBUG) {
        System.out.print("site := " + site + ", receiver := " + receiver[0]);
      }
      // If the first argument is a constant ...
      IR ir = caller.getIR();
      SymbolTable symbolTable = ir.getSymbolTable();
      SSAAbstractInvokeInstruction[] invokeInstructions = caller.getIR().getCalls(site);
      if (invokeInstructions.length != 1) {
        return null;
      }
      int use = invokeInstructions[0].getUse(1);
      if (symbolTable.isStringConstant(invokeInstructions[0].getUse(1))) {
        String sym = symbolTable.getStringValue(use);
        if (DEBUG) {
          System.out.println(invokeInstructions);
          System.out.println(", with constant := `" + sym + "`");
          for (InstanceKey instanceKey:receiver) {
            System.out.println(" " + instanceKey);
          }
        }
        // ... return an GetMethodContext.
        ConstantKey ck = makeConstantKey(caller.getClassHierarchy(),sym);
        System.out.println(ck);
        return new GetMethodContext(new PointType(getTypeConstant(receiver[0])),ck);
      }
      if (DEBUG) {
        System.out.println(", with constant := no");
      }
      // Otherwise, return null.
      // TODO Remove this, just fall-through.
      return null;
    }
    return null;
  }

  /**
   * @brief
   *  If @a instance is a ConstantKey and its value is an instance of IClass,
   *  return that value. Otherwise, return @a null.
   */
  private IClass getTypeConstant(InstanceKey instance) {
    if (instance instanceof ConstantKey) {
      ConstantKey c = (ConstantKey) instance;
      if (c.getValue() instanceof IClass) {
        return (IClass) c.getValue();
      }
    }
    return null;
  }
  
  /**
   * @brief
   *  Create a constant key for a string.
   * @param cha
   *  The class hierarchy.
   * @param str
   *  The string.
   * @return 
   *  The constant key.
   */
  protected static ConstantKey<String> makeConstantKey(IClassHierarchy cha,String str) {
    IClass cls = cha.lookupClass(TypeReference.JavaLangString);
    ConstantKey<String> ck = new ConstantKey<String>(str,cls);
    return ck;
  }

  private static final Collection<MethodReference> UNDERSTOOD_METHOD_REFS = HashSetFactory.make();

  static {
    UNDERSTOOD_METHOD_REFS.add(JavaLangClassContextInterpreter.GET_METHOD);
    UNDERSTOOD_METHOD_REFS.add(JavaLangClassContextInterpreter.GET_DECLARED_METHOD);
  }

  /**
   * @brief
   *  This object might understand a dispatch to
   *  {@link java.lang.Class#getMethod(String, Class...)}
   *  or
   *  {@link java.lang.Class#getDeclaredMethod}
   *  when the receiver is a type constant.
   */
  private boolean mayUnderstand(CGNode caller,CallSiteReference site,IMethod targetMethod,InstanceKey instance) {
    return UNDERSTOOD_METHOD_REFS.contains(targetMethod.getReference())
        && getTypeConstant(instance) != null;
  }

  /**
   * TODO
   *  MH: Shouldn't be the first TWO parameters be relevant?
   *      Documentation is not too helpful about the implications.
   */
  private static final IntSet thisParameter = IntSetUtil.make(new int[]{0});

  @Override
  public IntSet getRelevantParameters(CGNode caller, CallSiteReference site) {
    if (site.isDispatch() || site.getDeclaredTarget().getNumberOfParameters() > 0) {
      return thisParameter;
    } else {
      return EmptyIntSet.instance;
    }
  } 
}

// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.

package org.objectweb.asm.commons;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

/**
 * A {@link MethodVisitor} that remaps types with a {@link Remapper}.
 *
 * @author Eugene Kuleshov
 */
public class MethodRemapper extends MethodVisitor {

  /** The remapper used to remap the types in the visited field. */
  protected final Remapper remapper;

  /**
   * Constructs a new {@link MethodRemapper}. <i>Subclasses must not use this constructor</i>.
   * Instead, they must use the {@link #MethodRemapper(int,MethodVisitor,Remapper)} version.
   *
   * @param methodVisitor the method visitor this remapper must deleted to.
   * @param remapper the remapper to use to remap the types in the visited method.
   */
  public MethodRemapper(final MethodVisitor methodVisitor, final Remapper remapper) {
    this(Opcodes.ASM6, methodVisitor, remapper);
  }

  /**
   * Constructs a new {@link MethodRemapper}.
   *
   * @param api the ASM API version supported by this remapper. Must be one of {@link
   *     org.objectweb.asm.Opcodes#ASM4}, {@link org.objectweb.asm.Opcodes#ASM5} or {@link
   *     org.objectweb.asm.Opcodes#ASM6}.
   * @param methodVisitor the method visitor this remapper must deleted to.
   * @param remapper the remapper to use to remap the types in the visited method.
   */
  protected MethodRemapper(
      final int api, final MethodVisitor methodVisitor, final Remapper remapper) {
    super(api, methodVisitor);
    this.remapper = remapper;
  }

  @Override
  public AnnotationVisitor visitAnnotationDefault() {
    AnnotationVisitor annotationVisitor = super.visitAnnotationDefault();
    return annotationVisitor == null
        ? annotationVisitor
        : new AnnotationRemapper(api, annotationVisitor, remapper);
  }

  @Override
  public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
    AnnotationVisitor annotationVisitor =
        super.visitAnnotation(remapper.mapDesc(descriptor), visible);
    return annotationVisitor == null
        ? annotationVisitor
        : new AnnotationRemapper(api, annotationVisitor, remapper);
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(
      final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
    AnnotationVisitor annotationVisitor =
        super.visitTypeAnnotation(typeRef, typePath, remapper.mapDesc(descriptor), visible);
    return annotationVisitor == null
        ? annotationVisitor
        : new AnnotationRemapper(api, annotationVisitor, remapper);
  }

  @Override
  public AnnotationVisitor visitParameterAnnotation(
      final int parameter, final String descriptor, final boolean visible) {
    AnnotationVisitor annotationVisitor =
        super.visitParameterAnnotation(parameter, remapper.mapDesc(descriptor), visible);
    return annotationVisitor == null
        ? annotationVisitor
        : new AnnotationRemapper(api, annotationVisitor, remapper);
  }

  @Override
  public void visitFrame(
      final int type,
      final int nLocal,
      final Object[] local,
      final int nStack,
      final Object[] stack) {
    super.visitFrame(
        type, nLocal, remapFrameTypes(nLocal, local), nStack, remapFrameTypes(nStack, stack));
  }

  private Object[] remapFrameTypes(final int numTypes, final Object[] frameTypes) {
    if (frameTypes == null) {
      return frameTypes;
    }
    Object[] remappedFrameTypes = null;
    for (int i = 0; i < numTypes; ++i) {
      if (frameTypes[i] instanceof String) {
        if (remappedFrameTypes == null) {
          remappedFrameTypes = new Object[numTypes];
          System.arraycopy(frameTypes, 0, remappedFrameTypes, 0, numTypes);
        }
        remappedFrameTypes[i] = remapper.mapType((String) frameTypes[i]);
      }
    }
    return remappedFrameTypes == null ? frameTypes : remappedFrameTypes;
  }

  @Override
  public void visitFieldInsn(
      final int opcode, final String owner, final String name, final String descriptor) {
    super.visitFieldInsn(
        opcode,
        remapper.mapType(owner),
        remapper.mapFieldName(owner, name, descriptor),
        remapper.mapDesc(descriptor));
  }

  /** @deprecated */
  @Deprecated
  @Override
  public void visitMethodInsn(
      final int opcode, final String owner, final String name, final String descriptor) {
    if (api >= Opcodes.ASM5) {
      super.visitMethodInsn(opcode, owner, name, descriptor);
      return;
    }
    doVisitMethodInsn(opcode, owner, name, descriptor, opcode == Opcodes.INVOKEINTERFACE);
  }

  @Override
  public void visitMethodInsn(
      final int opcode,
      final String owner,
      final String name,
      final String descriptor,
      final boolean isInterface) {
    if (api < Opcodes.ASM5) {
      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      return;
    }
    doVisitMethodInsn(opcode, owner, name, descriptor, isInterface);
  }

  private void doVisitMethodInsn(
      final int opcode,
      final String owner,
      final String name,
      final String descriptor,
      final boolean isInterface) {
    // Calling super.visitMethodInsn requires to call the correct version depending on this.api
    // (otherwise infinite loops can occur). To simplify and to make it easier to automatically
    // remove the backward compatibility code, we inline the code of the overridden method here.
    if (mv != null) {
      mv.visitMethodInsn(
          opcode,
          remapper.mapType(owner),
          remapper.mapMethodName(owner, name, descriptor),
          remapper.mapMethodDesc(descriptor),
          isInterface);
    }
  }

  @Override
  public void visitInvokeDynamicInsn(
      final String name,
      final String descriptor,
      final Handle bootstrapMethodHandle,
      final Object... bootstrapMethodArguments) {
    Object[] remappedBootstrapMethodArguments = new Object[bootstrapMethodArguments.length];
    for (int i = 0; i < bootstrapMethodArguments.length; ++i) {
      remappedBootstrapMethodArguments[i] = remapper.mapValue(bootstrapMethodArguments[i]);
    }
    super.visitInvokeDynamicInsn(
        remapper.mapInvokeDynamicMethodName(name, descriptor),
        remapper.mapMethodDesc(descriptor),
        (Handle) remapper.mapValue(bootstrapMethodHandle),
        remappedBootstrapMethodArguments);
  }

  @Override
  public void visitTypeInsn(final int opcode, final String type) {
    super.visitTypeInsn(opcode, remapper.mapType(type));
  }

  @Override
  public void visitLdcInsn(final Object value) {
    super.visitLdcInsn(remapper.mapValue(value));
  }

  @Override
  public void visitMultiANewArrayInsn(final String descriptor, final int numDimensions) {
    super.visitMultiANewArrayInsn(remapper.mapDesc(descriptor), numDimensions);
  }

  @Override
  public AnnotationVisitor visitInsnAnnotation(
      final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
    AnnotationVisitor annotationVisitor =
        super.visitInsnAnnotation(typeRef, typePath, remapper.mapDesc(descriptor), visible);
    return annotationVisitor == null
        ? annotationVisitor
        : new AnnotationRemapper(api, annotationVisitor, remapper);
  }

  @Override
  public void visitTryCatchBlock(
      final Label start, final Label end, final Label handler, final String type) {
    super.visitTryCatchBlock(start, end, handler, type == null ? null : remapper.mapType(type));
  }

  @Override
  public AnnotationVisitor visitTryCatchAnnotation(
      final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
    AnnotationVisitor annotationVisitor =
        super.visitTryCatchAnnotation(typeRef, typePath, remapper.mapDesc(descriptor), visible);
    return annotationVisitor == null
        ? annotationVisitor
        : new AnnotationRemapper(api, annotationVisitor, remapper);
  }

  @Override
  public void visitLocalVariable(
      final String name,
      final String descriptor,
      final String signature,
      final Label start,
      final Label end,
      final int index) {
    super.visitLocalVariable(
        name,
        remapper.mapDesc(descriptor),
        remapper.mapSignature(signature, true),
        start,
        end,
        index);
  }

  @Override
  public AnnotationVisitor visitLocalVariableAnnotation(
      final int typeRef,
      final TypePath typePath,
      final Label[] start,
      final Label[] end,
      final int[] index,
      final String descriptor,
      final boolean visible) {
    AnnotationVisitor annotationVisitor =
        super.visitLocalVariableAnnotation(
            typeRef, typePath, start, end, index, remapper.mapDesc(descriptor), visible);
    return annotationVisitor == null
        ? annotationVisitor
        : new AnnotationRemapper(api, annotationVisitor, remapper);
  }
}

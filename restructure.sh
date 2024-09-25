#!/bin/bash

set -e

move () {
   cp -r $1 $2
   git add $2
}
ORIGIN=
TARGET=
movejava () {
    cp $ORIGIN/$1 $TARGET/$1
    git add $TARGET/$1
}

# install tools
pushd ideal/tools
mvn install
popd

#  jsinterop
# Set of annotations to mark types as mappable to JavaScript
mkdir -p ideal/jsinterop/src/main/java/jsinterop/annotations
move user/src/jsinterop/annotations ideal/jsinterop/src/main/java/jsinterop/annotations

pushd ideal/jsinterop
mvn clean install
popd


#  javaemul
# These utils and compiler hints are for internal use only, and are used in the lang project
mkdir -p ideal/javaemul/src/main/super/javaemul/
move user/super/com/google/gwt/emul/javaemul/internal \
     ideal/javaemul/src/main/super/javaemul/

pushd ideal/javaemul
mvn clean install
popd


#  core
# This consists of root GWT classes available to the user that are required to
# build and run the compiler (aside from JavaScriptObject)
mkdir -p ideal/core/src/main/java/com/google/gwt/core/client/{impl,prefetch} \
         ideal/core/src/main/java/com/google/gwt/core/shared/impl
#move user/src/com/google/gwt/core/client/EntryPoint.java \
#   ideal/core/src/main/java/com/google/gwt/core/client/EntryPoint.java # technically _not_ required!

# Direct dependencies from compiler
ORIGIN=user/src
TARGET=ideal/core/src/main/java
movejava com/google/gwt/core/client/GWT.java

movejava com/google/gwt/core/client/JavaScriptObject.java

movejava com/google/gwt/core/client/RunAsyncCallback.java

movejava com/google/gwt/core/client/impl/AsyncFragmentLoader.java

movejava com/google/gwt/core/client/impl/Impl.java

movejava com/google/gwt/core/client/prefetch/RunAsyncCode.java

# Transitive dependencies
movejava com/google/gwt/core/client/Scheduler.java

movejava com/google/gwt/core/shared/GWT.java

movejava com/google/gwt/core/client/impl/OnSuccessExecutor.java
movejava com/google/gwt/core/client/Duration.java
movejava com/google/gwt/core/client/JavaScriptException.java
movejava com/google/gwt/core/client/impl/JavaScriptExceptionBase.java
#movejava com/google/gwt/core/client/impl/StackTraceCreator.java #Depends on lang's ArrayHelper, moved to lang
movejava com/google/gwt/core/client/impl/SchedulerImpl.java

movejava com/google/gwt/core/client/JsArray.java
movejava com/google/gwt/core/client/JsArrayString.java

movejava com/google/gwt/core/client/JsDate.java
movejava com/google/gwt/core/shared/impl/JsLogger.java

movejava com/google/gwt/core/client/prefetch/PrefetchableResource.java


ORIGIN=dev/core/super
movejava com/google/gwt/core/shared/GWTBridge.java
movejava com/google/gwt/core/client/GWTBridge.java
movejava com/google/gwt/core/client/GwtScriptOnly.java


pushd ideal/core
mvn clean install
popd



#  lang
# Classes required to build and run the compiler, but not available to the user
# Normally these need not be compiled to bytecode, but ArrayHelper is used by core.
mkdir -p ideal/lang/src/main/super/com/google/gwt/dev/
mkdir -p ideal/lang/src/main/java/com/google/gwt/{core,lang}/
mkdir -p ideal/lang/src/main/java/com/google/gwt/core/client/impl/
# many of these types require core's JavaScriptObject

move dev/core/super/com/google/gwt/dev/jjs \
ideal/lang/src/main/super/com/google/gwt/dev/
move dev/core/super/com/google/gwt/lang \
ideal/lang/src/main/java/com/google/gwt/
move dev/core/super/com/google/gwt/core \
ideal/lang/src/main/java/com/google/gwt/

ORIGIN=user/src
TARGET=ideal/lang/src/main/java
movejava com/google/gwt/core/client/impl/StackTraceCreator.java #Depends on lang's ArrayHelper, and with broken loop, can live here


pushd ideal/lang
mvn clean install
popd

#  utils
# Classes required by most of the compiler, generators, linkers, command line tools...
mkdir -p ideal/util/src/main/java/com/google/gwt/dev/util/log/speedtracer \
         ideal/util/src/main/java/com/google/gwt/util/tools/shared \
         ideal/util/src/main/java/com/google/gwt/core/ext
ORIGIN=dev/core/src
TARGET=ideal/util/src/main/java

move dev/core/src/com/google/gwt/dev/util/collect \
     ideal/util/src/main/java/com/google/gwt/dev/

movejava com/google/gwt/dev/util/Util.java #depends on TreeLogger, UnableToCompleteException,
# speedtracer...

movejava com/google/gwt/dev/util/StringInterningObjectInputStream.java
movejava com/google/gwt/util/tools/Utility.java
movejava com/google/gwt/util/tools/shared/StringUtils.java

#movejava com/google/gwt/core/ext/TreeLogger.java
movejava com/google/gwt/core/ext/UnableToCompleteException.java
#movejava com/google/gwt/dev/util/log/speedtracer/SpeedTracerEventType.java
#movejava com/google/gwt/dev/util/log/speedtracer/CompilerEventType.java
#movejava com/google/gwt/dev/util/log/speedtracer/SpeedTracerLogger.java
movejava com/google/gwt/dev/util/StringInterner.java #depends on rebased guava19
movejava com/google/gwt/dev/util/DefaultTextOutput.java
movejava com/google/gwt/dev/util/AbstractTextOutput.java
movejava com/google/gwt/dev/util/TextOutput.java


# HAAAAACK. Not really sure where this ought to belong, but ugly here.
# Also, need to replace strings in the Abouts
movejava com/google/gwt/dev/About.java
movejava com/google/gwt/dev/About.properties
movejava com/google/gwt/dev/GwtVersion.java

pushd ideal/util
mvn clean install
popd

#  linker/generator api
# GWT 1.x/2.x API to generate code and affect final compiled output
mkdir -p ideal/ext/src/main/java/com/google/gwt/core/ext/typeinfo \
         ideal/ext/src/main/java/com/google/gwt/core/ext/linker/impl \
         ideal/ext/src/main/java/com/google/gwt/dev/{resource,util}/

ORIGIN=dev/core/src
TARGET=ideal/ext/src/main/java

movejava com/google/gwt/core/ext/Generator.java
movejava com/google/gwt/core/ext/IncrementalGenerator.java
movejava com/google/gwt/core/ext/GeneratorContext.java
movejava com/google/gwt/core/ext/TreeLogger.java
#movejava com/google/gwt/core/ext/UnableToCompleteException.java    #living in util for now
movejava com/google/gwt/core/ext/RebindResult.java
movejava com/google/gwt/core/ext/NonIncrementalGeneratorContext.java
movejava com/google/gwt/core/ext/DelegatingGeneratorContext.java
movejava com/google/gwt/core/ext/CachedGeneratorResult.java
movejava com/google/gwt/core/ext/RebindMode.java
movejava com/google/gwt/core/ext/PropertyOracle.java
movejava com/google/gwt/core/ext/ConfigurationProperty.java
movejava com/google/gwt/core/ext/SelectionProperty.java
movejava com/google/gwt/core/ext/BadPropertyValueException.java

move dev/core/src/com/google/gwt/core/ext/typeinfo \
     ideal/ext/src/main/java/com/google/gwt/core/ext/

movejava com/google/gwt/core/ext/Linker.java
movejava com/google/gwt/core/ext/LinkerContext.java
movejava com/google/gwt/core/ext/linker/Shardable.java
movejava com/google/gwt/core/ext/linker/Artifact.java
movejava com/google/gwt/core/ext/linker/EmittedArtifact.java
movejava com/google/gwt/core/ext/linker/GeneratedResource.java
movejava com/google/gwt/core/ext/linker/ArtifactSet.java
movejava com/google/gwt/core/ext/linker/TypeIndexedSet.java #depends on rebased guava19
movejava com/google/gwt/core/ext/linker/SelectionProperty.java
movejava com/google/gwt/core/ext/linker/ConfigurationProperty.java
movejava com/google/gwt/core/ext/linker/Transferable.java

movejava com/google/gwt/core/ext/linker/ScriptReference.java
movejava com/google/gwt/core/ext/linker/impl/ResourceInjectionUtil.java

movejava com/google/gwt/core/ext/linker/SoftPermutation.java
movejava com/google/gwt/core/ext/linker/StatementRanges.java
movejava com/google/gwt/core/ext/linker/SymbolData.java
movejava com/google/gwt/core/ext/linker/StylesheetReference.java
movejava com/google/gwt/core/ext/linker/AbstractLinker.java
movejava com/google/gwt/core/ext/linker/SyntheticArtifact.java


movejava com/google/gwt/dev/resource/ResourceOracle.java
movejava com/google/gwt/dev/resource/Resource.java

movejava com/google/gwt/dev/util/DiskCache.java
movejava com/google/gwt/dev/util/StringKey.java

# okay, lets just move everything in these packages and not worry about exposing classes
# downstream shouldn't see
#move dev/core/src/com/google/gwt/core/ext/linker \
#     ideal/ext/src/main/java/com/google/gwt/core/ext/

# no? just copy what seems to be a published API
movejava com/google/gwt/core/ext/linker/CompilationResult.java
movejava com/google/gwt/core/ext/linker/LinkerOrder.java





pushd ideal/ext
mvn clean install
popd


#  built-in linkers for output that don't depend on the compiler (i.e. no soyc?)
mkdir -p ideal/linkers/src/main/java/com/google/gwt/core/ext/linker/impl

ORIGIN=dev/core/src
TARGET=ideal/linkers/src/main/java

move dev/core/src/com/google/gwt/core/linker \
     ideal/linkers/src/main/java/com/google/gwt/core/

movejava com/google/gwt/core/ext/linker/impl/PropertiesMappingArtifact.java #used by xsiframe only?
movejava com/google/gwt/core/ext/linker/impl/SelectionScriptLinker.java
movejava com/google/gwt/core/ext/linker/impl/SelectionInformation.java
movejava com/google/gwt/core/ext/linker/impl/PropertiesUtil.java
movejava com/google/gwt/core/ext/linker/impl/PermutationsUtil.java

# deliberately not included, soyc lives in compiler for now?
#movejava com/google/gwt/core/ext/linker/impl/ModuleMetricsArtifact.java
# restore symbolmaps/soyc for now
TARGET=dev/core/src
ORIGIN=ideal/linkers/src/main/java
mkdir -p dev/core/src/com/google/gwt/core/linker/
movejava com/google/gwt/core/linker/SoycReportLinker.java
#movejava com/google/gwt/core/linker/SymbolMapsLinker.java
#workaround until move/movejava actually mvs
rm ideal/linkers/src/main/java/com/google/gwt/core/linker/SoycReportLinker.java
#rm ideal/linkers/src/main/java/com/google/gwt/core/linker/SymbolMapsLinker.java

pushd ideal/linkers
mvn clean install
popd


#  emul
# Java Language emulation, basics required to compile at all
# JRE emulation, support for applications that depend on greater aspects of Java

mkdir -p ideal/emul/base/src/main/super/java/{io,lang,util}

ORIGIN=user/super/com/google/gwt/emul
TARGET=ideal/emul/base/src/main/super

movejava java/io/Serializable.java
movejava java/lang/Object.java
movejava java/lang/String.java
movejava java/lang/Class.java
movejava java/lang/CharSequence.java
movejava java/lang/Cloneable.java
movejava java/lang/Comparable.java
movejava java/lang/Enum.java
movejava java/lang/Iterable.java
movejava java/util/Iterator.java
movejava java/lang/AssertionError.java
movejava java/lang/Boolean.java
movejava java/lang/Byte.java
movejava java/lang/Character.java
movejava java/lang/Short.java
movejava java/lang/Integer.java
movejava java/lang/Long.java
movejava java/lang/Float.java
movejava java/lang/Double.java
movejava java/lang/Throwable.java

pushd ideal/emul/base
mvn clean install
popd

mkdir -p ideal/emul/jre/src/main/super/

move user/super/com/google/gwt/emul/java ideal/emul/jre/src/main/super/

pushd ideal/emul/jre
mvn clean install
popd

pushd ideal/emul
mvn clean install
popd


# dev mode
# We build this before the compiler (which it depends on) to remove classes from the compiler to
# just bulk copy the rest
mkdir -p ideal/dev/devmode/src/main/java/com/google/gwt/dev/{shell,ui}
ORIGIN=dev/core/src
TARGET=ideal/dev/devmode/src/main/java

movejava com/google/gwt/dev/DevelModeTabKey.java
movejava com/google/gwt/dev/DevMode.java
movejava com/google/gwt/dev/DevModeBase.java
movejava com/google/gwt/dev/Disconnectable.java
movejava com/google/gwt/dev/HeadlessUI.java
movejava com/google/gwt/dev/HostedMode.java
movejava com/google/gwt/dev/GWTMain.java
movejava com/google/gwt/dev/ModuleHandle.java
movejava com/google/gwt/dev/ModulePanel.java
movejava com/google/gwt/dev/ModuleTabPanel.java
movejava com/google/gwt/dev/RunWebApp.java
movejava com/google/gwt/dev/SessionModule.java
movejava com/google/gwt/dev/ServletValidator.java
movejava com/google/gwt/dev/SwingUI.java
movejava com/google/gwt/dev/WebServerPanel.java
movejava com/google/gwt/dev/ui/DevModeUI.java

move dev/core/src/com/google/gwt/dev/ui ideal/dev/devmode/src/main/java/com/google/gwt/dev
move dev/core/src/com/google/gwt/dev/shell/remoteui \
     ideal/dev/devmode/src/main/java/com/google/gwt/dev/shell/
move dev/core/src/com/google/gwt/dev/shell/rewrite \
     ideal/dev/devmode/src/main/java/com/google/gwt/dev/shell/
move dev/core/src/com/google/gwt/dev/shell/jetty \
     ideal/dev/devmode/src/main/java/com/google/gwt/dev/shell/
move dev/core/src/com/google/gwt/dev/shell/log \
     ideal/dev/devmode/src/main/java/com/google/gwt/dev/shell/

# CheckForUpdates, ie is an exception, it is used by the compiler, walk it manually
#movejava com/google/gwt/dev/shell/*
movejava com/google/gwt/dev/shell/BrowserChannel.java
movejava com/google/gwt/dev/shell/BrowserChannelClient.java
movejava com/google/gwt/dev/shell/BrowserChannelException.java
movejava com/google/gwt/dev/shell/BrowserChannelServer.java
movejava com/google/gwt/dev/shell/BrowserListener.java
movejava com/google/gwt/dev/shell/BrowserWidgetHost.java
movejava com/google/gwt/dev/shell/CloseButton.java
movejava com/google/gwt/dev/shell/CodeServerListener.java
movejava com/google/gwt/dev/shell/CompilingClassLoader.java
movejava com/google/gwt/dev/shell/DispatchClassInfo.java
movejava com/google/gwt/dev/shell/DispatchIdOracle.java
movejava com/google/gwt/dev/shell/EmmaStrategy.java
movejava com/google/gwt/dev/shell/GWTBridgeImpl.java
movejava com/google/gwt/dev/shell/JavaDispatchImpl.java
movejava com/google/gwt/dev/shell/JavaScriptHost.java
movejava com/google/gwt/dev/shell/Jsni.java
movejava com/google/gwt/dev/shell/JsValue.java
movejava com/google/gwt/dev/shell/JsValueGlue.java
movejava com/google/gwt/dev/shell/JsValueOOPHM.java
movejava com/google/gwt/dev/shell/MethodDispatch.java
movejava com/google/gwt/dev/shell/ModuleSpace.java
movejava com/google/gwt/dev/shell/ModuleSpaceHost.java
movejava com/google/gwt/dev/shell/ModuleSpaceOOPHM.java
movejava com/google/gwt/dev/shell/ModuleSpacePropertyOracle.java
movejava com/google/gwt/dev/shell/OophmSessionHandler.java
movejava com/google/gwt/dev/shell/RemoteObjectTable.java
movejava com/google/gwt/dev/shell/ServerMethods.java
movejava com/google/gwt/dev/shell/ServerObjectsTable.java
movejava com/google/gwt/dev/shell/ShellJavaScriptHost.java
movejava com/google/gwt/dev/shell/ShellMainWindow.java
movejava com/google/gwt/dev/shell/ShellModuleSpaceHost.java
movejava com/google/gwt/dev/shell/SuperDevListener.java
movejava com/google/gwt/dev/shell/SyntheticClassMember.java
movejava com/google/gwt/dev/shell/WrapLayout.java

# images
move dev/core/src/com/google/gwt/dev/shell/*.png \
     ideal/dev/devmode/src/main/java/com/google/gwt/dev/shell/
move dev/core/src/com/google/gwt/dev/shell/*.gif \
     ideal/dev/devmode/src/main/java/com/google/gwt/dev/shell/

# Junit classes for devmode-htmlunit interaction
TARGET=ideal/dev/junit3/src/main/java
mkdir -p ideal/dev/junit3/src/main/java/com/google/gwt/dev/shell/
movejava com/google/gwt/dev/shell/HostedModePluginObject.java
movejava com/google/gwt/dev/shell/HtmlUnitSessionHandler.java
movejava com/google/gwt/dev/shell/JavaObject.java
movejava com/google/gwt/dev/shell/SessionData.java

#  compiler
# Java to JavaScript Compiler
# Dependencies, which must be present in core, lang:
#  * com.google.gwt.dev.jjs.ast.JProgram#buildInitialTypeNamesToIndex (includes CODEGEN_TYPES_SET)
#  * com.google.gwt.dev.jjs.ast.JProgram#IMMORTAL_CODEGEN_TYPES_SET

mkdir -p ideal/dev/compiler/src/main/java/com/google/gwt/
move dev/core/src/com/google/gwt/core ideal/dev/compiler/src/main/java/com/google/gwt/
move dev/core/src/com/google/gwt/dev ideal/dev/compiler/src/main/java/com/google/gwt/
move dev/core/src/com/google/gwt/soyc ideal/dev/compiler/src/main/java/com/google/gwt/
move dev/core/src/com/google/gwt/util ideal/dev/compiler/src/main/java/com/google/gwt/


#  test tools (junit)
# TODO split this again, so that the API is in one jar, and the running magic in another?
# TODO this also depends on a lot of user stuff: safehtml, rpc, etc
mkdir -p ideal/dev/junit3/src/main/java/com/google/gwt
move user/src/com/google/gwt/junit ideal/dev/junit3/src/main/java/com/google/gwt/

pushd ideal/dev
mvn clean install
popd

#  tools



#  user
#


#  integration tests


#  samples
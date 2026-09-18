/*
 * Copyright 2026 GWT Project Authors
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
package com.google.gwt.dev.js;

import com.google.gwt.dev.cfg.ConfigurationProperties;
import com.google.gwt.dev.js.ast.JsContext;
import com.google.gwt.dev.js.ast.JsForIn;
import com.google.gwt.dev.js.ast.JsFunction;
import com.google.gwt.dev.js.ast.JsLabel;
import com.google.gwt.dev.js.ast.JsName;
import com.google.gwt.dev.js.ast.JsNameOf;
import com.google.gwt.dev.js.ast.JsNameRef;
import com.google.gwt.dev.js.ast.JsParameter;
import com.google.gwt.dev.js.ast.JsProgram;
import com.google.gwt.dev.js.ast.JsScope;
import com.google.gwt.dev.js.ast.JsVars;
import com.google.gwt.dev.js.ast.JsVisitor;
import com.google.gwt.dev.util.DefaultTextOutput;
import com.google.gwt.thirdparty.guava.common.collect.HashMultiset;
import com.google.gwt.thirdparty.guava.common.collect.ImmutableMultiset;
import com.google.gwt.thirdparty.guava.common.collect.Multiset;
import com.google.gwt.thirdparty.guava.common.collect.Multisets;

import java.util.*;

/**
 * A namer that uses short, unrecognizable idents to minimize generated code
 * size. Counts occurrences of each symbol before starting to rename, so that more frequently
 * used symbols are allotted shorter names.
 * <p>
 * There are two hierarchies of names here - like other namers, we visit the object and top scopes,
 * and recursively visit all child scopes.
 * <p>
 * Not suitable for use in incremental compilation, as names will change based on usage counts.
 */
public class JsCountingObfuscateNamer implements FreshNameGenerator {

  /**
   * A lookup table of base-64 chars we use to encode idents.
   */
  private static final byte[] sBase64Chars = new byte[]{
      'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
      'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B',
      'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
      'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '$', '_', '0', '1',
      '2', '3', '4', '5', '6', '7', '8', '9'};

  public static FreshNameGenerator exec(JsProgram program) throws JsNamer.IllegalNameException {
    return exec(program, null);
  }

  /**
   * Processes the program in anticipation of obfuscating names.
   *
   * @param program the JS program to process
   * @param config the configuration properties to use
   * @return a FreshNameGenerator that can be used to generate fresh names
   * @throws JsNamer.IllegalNameException
   */
  public static FreshNameGenerator exec(JsProgram program, ConfigurationProperties config)
      throws JsNamer.IllegalNameException {
    JsCountingObfuscateNamer namer = new JsCountingObfuscateNamer(program, config);

    // Across distinct passes, we must also track the "total high water mark" so that the returned
    // namer can be used to generate unique fresh names.
    namer.visit(program.getScope());
    // Reset the local high water mark so we can reuse idents in the new scope
    namer.maxChildId = 0;
    namer.visit(program.getObjectScope());

    return namer;
  }

  private static ImmutableMultiset<JsName> countReferences(JsProgram program) {
    Multiset<JsName> nameCounts = HashMultiset.create();
    new JsVisitor() {
      @Override
      public void endVisit(JsForIn x, JsContext ctx) {
        reference(x.getIterVarName());
      }

      @Override
      public void endVisit(JsFunction x, JsContext ctx) {
        reference(x.getName());
      }

      @Override
      public void endVisit(JsLabel x, JsContext ctx) {
        reference(x.getName());
      }

      @Override
      public void endVisit(JsNameOf x, JsContext ctx) {
        reference(x.getName());
      }

      @Override
      public void endVisit(JsNameRef x, JsContext ctx) {
        reference(x.getName());
      }

      @Override
      public void endVisit(JsParameter x, JsContext ctx) {
        reference(x.getName());
      }

      @Override
      public void endVisit(JsVars.JsVar x, JsContext ctx) {
        reference(x.getName());
      }

      private void reference(JsName name) {
        if (name != null) {
          nameCounts.add(name);
        }
      }
    }.accept(program);

    // Sort the names by their counts, and produce a map from name to rank. Names with the
    // highest reference count get the lowest rank (0), so they are handed the shortest idents.
    return Multisets.copyHighestCountFirst(nameCounts);
  }

  /**
   * Returns a valid unused obfuscated top scope name by keeping track of the last (highest)
   * name produced. We've already allocated the "good" names, so this comes from the bottom of the
   * barrel.
   */
  @Override
  public String getFreshName() {
    String newIdent;
    do {
      // Get the next possible obfuscated name
      newIdent = makeObfuscatedIdent(maxId++, orderedBase54, orderedBase64);
    } while (!isLegal(program.getScope(), newIdent));
    return newIdent;
  }

  private final ReservedNames reserved;
  private final JsProgram program;

  /**
   * Maps each referenced name to its rank by descending reference count (rank 0 is the most
   * frequently referenced). Populated by {@link #countReferences(JsProgram)} and used to allocate the
   * shortest idents to the highest-ranked names.
   */
  private final Multiset<JsName> referenceCounts;

  /**
   * Communicates to a parent scope the maximum id used by any of its children.
   */
  private int maxChildId = 0;

  /**
   * Remember the maximum ChildIdAssigned so that new names can safely be obtained without
   * running the global renaming again.
   */
  private int maxId = -1;
  private final byte[] orderedBase54 = new byte[54];
  private final byte[] orderedBase64 = new byte[64];

  public JsCountingObfuscateNamer(JsProgram program, ConfigurationProperties config) {
    this.program = program;
    reserved = new ReservedNames(config);

    // Walk the program and count references, sorting so that most frequently used names are first
    referenceCounts = countReferences(program);
    DefaultTextOutput out = new DefaultTextOutput(false);
    JsToStringGenerationVisitor v = new JsToStringGenerationVisitor(out) {
      @Override
      protected String getIdent(JsName name) {
        return "";
      }

      @Override
      protected String getIdent(JsNameRef name) {
        return "";
      }
    };
    v.accept(program);
    String plain = out.toString();
    HashMap<Integer, Integer> frequencies = new HashMap<>();
    for (int i = 0; i < plain.length(); i++) {
      char c = plain.charAt(i);
      frequencies.put((int) c, frequencies.getOrDefault((int) c,  0) + 1);
    }
    Integer[] base64int = new Integer[64];
    for (int i = 0; i < sBase64Chars.length; i++) {
      base64int[i] = (int) sBase64Chars[i];
    }
    Arrays.sort(base64int, Comparator.comparingInt((c) -> -frequencies.getOrDefault(c, 0)));
    int alpha = 0;
    for (int i = 0; i < orderedBase64.length; i++) {
      int next = base64int[i];
      orderedBase64[i] = (byte) next;
      if (next < '0' || next > '9') {
        orderedBase54[alpha++] = (byte) next;
      }
    }
  }

  protected void visit(JsScope scope) {
    // Save off the maxChildId which is currently being computed for my parent.
    int mySiblingsMaxId = maxChildId;

    /*
     * Visit my children first. Reset maxChildId so that my children will get a
     * clean slate: I do not communicate to my children.
     */
    maxChildId = 0;
    for (JsScope child : scope.getChildren()) {
      visit(child);
    }
    // maxChildId is now the max of all of my children's ids

    // Visit my idents - this is two passes over the scope, first to collect the references actually
    // used in this env, then to iterate those used names and obfsucate them, skipping disallowed
    // names as needed, and tracking the high water mark from this set/subset.
    int curId = maxChildId;
    List<JsName> usedNames = new ArrayList<>();
    for (JsName name : scope.getAllNames()) {
      int count = referenceCounts.count(name);
      if (count == 0) {
        // Can't/shouldn't allocate idents for non-referenced names.
        continue;
      }

      if (!name.isObfuscatable()) {
        // Unobfuscatable names become themselves, ignore
        name.setShortIdent(name.getIdent());
        continue;
      }
      usedNames.add(name);
    }

    if (usedNames.size() > 50) {
      List<JsName> sorted = usedNames.stream().sorted(
              Comparator.comparingInt(referenceCounts::count).reversed()).toList();
      usedNames.sort(Comparator.comparingInt(name ->
              (int) Math.floor(Math.log(sorted.indexOf(name) / Math.log(64)))));
    }

    // Filter the global counts to just this scope's names, and assign smallest idents to most-used names
    String newIdent;
    for (JsName name : usedNames) {
      do {
        // Get the next shortest obfuscated name that is legal
        newIdent = makeObfuscatedIdent(curId++, orderedBase54, orderedBase64);
      } while (!isLegal(scope, newIdent));

      name.setShortIdent(newIdent);
    }
    // Record the new high water marks
    maxChildId = Math.max(mySiblingsMaxId, curId);
    maxId = Math.max(maxId, maxChildId);
  }

  private boolean isLegal(JsScope scope, String newIdent) {
    if (!reserved.isAvailable(newIdent)) {
      return false;
    }
    /*
     * Never obfuscate a name into an identifier that conflicts with an existing
     * unobfuscatable name! It's okay if it conflicts with an existing
     * obfuscatable name, since that name will get obfuscated to something else
     * anyway.
     */
    return (scope.findExistingUnobfuscatableName(newIdent) == null);
  }

  public static String makeObfuscatedIdent(int id) {
    return makeObfuscatedIdent(id, sBase64Chars, sBase64Chars);
  }

  private static String makeObfuscatedIdent(int id, byte[] orderedBase54, byte[] orderedBase64) {
    byte[] sIdentBuf = new byte[6];

    // Use base-54 for the first character of the identifier,
    // so that we don't use any numbers (which are illegal at
    // the beginning of an identifier).
    //
    int i = 0;
    sIdentBuf[i++] = orderedBase54[id % 54];
    id /= 54;

    // Use base-64 for the rest of the identifier.
    //
    while (id != 0) {
      sIdentBuf[i++] = orderedBase64[id & 0x3f];
      id >>= 6;
    }

    return new String(sIdentBuf, 0, i);
  }
}

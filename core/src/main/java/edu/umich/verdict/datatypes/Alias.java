/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.umich.verdict.datatypes;

/**
 * Introduced for BootstrapSelectStatementRewriter. Remembers what was the
 * original column name user wanted. This class is helpful when introducing many
 * nested outer queries.
 * 
 * @author Yongjoo Park
 *
 */
public class Alias implements Comparable<Alias> {

    private boolean autoGenerated;

    private static int aliasIndex = 0;

    // This field is more of a name that should be displayed to the user
    private String originalName; // set to "C" for "COUNT(*) AS C"

    private String aliasName;

    public Alias(String originalName, String aliasName) {
        autoGenerated = false;
        this.originalName = originalName;
        this.aliasName = aliasName;
    }

    public static void resetAliasIndex() {
        aliasIndex = 0;
    }

    public static Alias genAlias(int depth, String originalName) {
        aliasIndex++;
        String aliasName = String.format("v%d_%d", depth, aliasIndex);
        Alias newAlias = new Alias(originalName, aliasName);
        newAlias.autoGenerated = true;
        return newAlias;
    }

    public static Alias genDerivedTableAlias(int depth) {
        return genAlias(depth, "subquery");
    }

    public String originalName() {
        return originalName;
    }

    public String aliasName() {
        return aliasName;
    }

    public boolean autoGenerated() {
        return autoGenerated;
    }

    public String getProperName() {
        if (autoGenerated)
            return originalName;
        else
            return aliasName;
    }

    @Override
    public String toString() {
        return aliasName;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object another) {
        if (another instanceof Alias) {
            Alias that = (Alias) another;
            if (this.originalName.equals(that.originalName) && this.aliasName.equals(that.aliasName)) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(Alias o) {
        if (this.originalName.equals(o.originalName)) {
            return this.aliasName.compareTo(o.aliasName);
        } else {
            return this.originalName.compareTo(o.originalName);
        }
    }

}

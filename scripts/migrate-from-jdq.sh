#!/usr/bin/env bash
# Migrates a project from spring-jpa-dynamic-query <= 0.4.x or spring-data-jpa-dynamic-query 0.5+ (com.beyt.jdq)
# to jpa-query-forge.
#
# Usage: scripts/migrate-from-jdq.sh <project-dir> [forge-version]
#
# - Rewrites imports and renamed types in every *.java file that references com.beyt.jdq.
#   Files without such a reference are never touched, so unrelated classes named e.g. QueryBuilder are safe.
# - Replaces the Maven dependency in <project-dir>/pom.xml.
# Requires perl (available by default on Linux and macOS). Commit or back up before running.
set -euo pipefail

PROJECT_DIR="${1:?usage: $0 <project-dir> [forge-version]}"
FORGE_VERSION="${2:-0.1.0-SNAPSHOT}"
P="io.github.apertechprojects.queryforge"

if [ ! -d "$PROJECT_DIR" ]; then
    echo "Not a directory: $PROJECT_DIR" >&2
    exit 1
fi

# Fully-qualified names first (most specific first), then simple names.
read -r -d '' JAVA_RULES <<PERL || true
# 0.5+ (multi-module) layout
s/\bcom\.beyt\.jdq\.jpa\.repository\.JpaDynamicQueryRepository\b/$P.repository.ForgeRepository/g;
s/\bcom\.beyt\.jdq\.jpa\.annotation\.EnableJpaDynamicQuery\b/$P.annotation.EnableQueryForge/g;
s/\bcom\.beyt\.jdq\.core\.annotation\.EnableDynamicQueryArgumentResolvers\b/$P.annotation.EnableQueryForgeWebArgumentResolvers/g;
s/\bcom\.beyt\.jdq\.jpa\.query\.builder\.QueryBuilder\b/$P.query.ForgeQueryBuilder/g;
s/\bcom\.beyt\.jdq\.core\.model\.annotation\.JdqModel\b/$P.annotation.ForgeModel/g;
s/\bcom\.beyt\.jdq\.core\.model\.annotation\.JdqField\b/$P.annotation.ForgeField/g;
s/\bcom\.beyt\.jdq\.core\.model\.annotation\.JdqIgnoreField\b/$P.annotation.ForgeIgnore/g;
s/\bcom\.beyt\.jdq\.core\.model\.annotation\.JdqSubModel\b/$P.annotation.ForgeSubModel/g;
s/\bcom\.beyt\.jdq\.core\.model\.enums\./$P.criteria./g;
s/\bcom\.beyt\.jdq\.core\.model\.exception\.DynamicQuery(\w*Exception)\b/$P.exception.QueryForge\1/g;
s/\bcom\.beyt\.jdq\.core\.model\.builder\.QuerySimplifier\b/$P.query.Forge/g;
s/\bcom\.beyt\.jdq\.core\.model\.builder\.interfaces\./$P.query.builder./g;
s/\bcom\.beyt\.jdq\.core\.model\.DynamicQuery\b/$P.query.ForgeQuery/g;
s/\bcom\.beyt\.jdq\.core\.model\.(Criteria|CriteriaList)\b/$P.criteria.\1/g;
s/\bcom\.beyt\.jdq\.core\.deserializer\.BasicDeserializer\b/$P.deserializer.DefaultValueDeserializer/g;
s/\bcom\.beyt\.jdq\.core\.deserializer\.IDeserializer\b/$P.deserializer.ValueDeserializer/g;
s/\bcom\.beyt\.jdq\.core\.util\.ListConsumer\b/$P.repository.ListConsumer/g;
# <= 0.4.x layout
s/\bcom\.beyt\.jdq\.repository\.JpaDynamicQueryRepository\b/$P.repository.ForgeRepository/g;
s/\bcom\.beyt\.jdq\.annotation\.EnableJpaDynamicQueryArgumentResolvers\b/$P.annotation.EnableQueryForgeWebArgumentResolvers/g;
s/\bcom\.beyt\.jdq\.annotation\.EnableJpaDynamicQuery\b/$P.annotation.EnableQueryForge/g;
s/\bcom\.beyt\.jdq\.annotation\.model\.JdqModel\b/$P.annotation.ForgeModel/g;
s/\bcom\.beyt\.jdq\.annotation\.model\.JdqField\b/$P.annotation.ForgeField/g;
s/\bcom\.beyt\.jdq\.annotation\.model\.JdqIgnoreField\b/$P.annotation.ForgeIgnore/g;
s/\bcom\.beyt\.jdq\.annotation\.model\.JdqSubModel\b/$P.annotation.ForgeSubModel/g;
s/\bcom\.beyt\.jdq\.dto\.enums\./$P.criteria./g;
s/\bcom\.beyt\.jdq\.dto\.DynamicQuery\b/$P.query.ForgeQuery/g;
s/\bcom\.beyt\.jdq\.dto\./$P.criteria./g;
s/\bcom\.beyt\.jdq\.query\.builder\.QuerySimplifier\b/$P.query.Forge/g;
s/\bcom\.beyt\.jdq\.query\.builder\.QueryBuilder\b/$P.query.ForgeQueryBuilder/g;
s/\bcom\.beyt\.jdq\.query\.builder\.interfaces\./$P.query.builder./g;
s/\bcom\.beyt\.jdq\.deserializer\.BasicDeserializer\b/$P.deserializer.DefaultValueDeserializer/g;
s/\bcom\.beyt\.jdq\.deserializer\.IDeserializer\b/$P.deserializer.ValueDeserializer/g;
s/\bcom\.beyt\.jdq\.util\.ListConsumer\b/$P.repository.ListConsumer/g;
s/\bcom\.beyt\.jdq\.provider\.IEntityManagerProvider\b/$P.context.EntityManagerProvider/g;
s/\bcom\.beyt\.jdq\.exception\.DynamicQuery(\w*Exception)\b/$P.exception.QueryForge\1/g;
s/\bDynamicQuery(\w+Exception)\b/QueryForge\1/g;
s/\bJpaDynamicQueryRepository\b/ForgeRepository/g;
s/\bEnableJpaDynamicQueryArgumentResolvers\b/EnableQueryForgeWebArgumentResolvers/g;
s/\bEnableDynamicQueryArgumentResolvers\b/EnableQueryForgeWebArgumentResolvers/g;
s/\bEnableJpaDynamicQuery\b/EnableQueryForge/g;
s/\bJdqModel\b/ForgeModel/g;
s/\bJdqField\b/ForgeField/g;
s/\bJdqIgnoreField\b/ForgeIgnore/g;
s/\bJdqSubModel\b/ForgeSubModel/g;
s/\bQuerySimplifier\b/Forge/g;
s/\bQueryBuilder\b/ForgeQueryBuilder/g;
s/\bDynamicQuery\b/ForgeQuery/g;
s/\bBasicDeserializer\b/DefaultValueDeserializer/g;
s/\bIDeserializer\b/ValueDeserializer/g;
s/\bIEntityManagerProvider\b/EntityManagerProvider/g;
s/\bParantesis\(/Parenthesis(/g;
PERL

count=0
while IFS= read -r -d '' file; do
    perl -pi -e "$JAVA_RULES" "$file"
    count=$((count + 1))
done < <(grep -rlZ --include='*.java' 'com\.beyt\.jdq' "$PROJECT_DIR" || true)
echo "Rewrote $count Java file(s)."

POM="$PROJECT_DIR/pom.xml"
if [ -f "$POM" ] && grep -q 'jpa-dynamic-query' "$POM"; then
    perl -0pi -e "s{<groupId>\s*io\.github\.tdilber\s*</groupId>(\s*)<artifactId>\s*spring(?:-boot-starter|-data)?-jpa-dynamic-query\s*</artifactId>(\s*)<version>[^<]*</version>}{<groupId>io.github.apertechprojects</groupId>\1<artifactId>jpa-query-forge</artifactId>\2<version>$FORGE_VERSION</version>}g" "$POM"
    echo "Updated dependency in $POM."
fi

leftovers=$(grep -rn --include='*.java' --include='pom.xml' -e 'com\.beyt' -e 'io\.github\.tdilber' "$PROJECT_DIR" || true)
if [ -n "$leftovers" ]; then
    echo "WARNING: references to the old library remain:" >&2
    echo "$leftovers" >&2
    exit 2
fi
echo "Done. No references to com.beyt.jdq remain."

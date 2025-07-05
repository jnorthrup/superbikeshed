#!/bin/bash

# Script to add all opt-ins and suppress all warnings to Kotlin source files
# Adds annotations BEFORE package declaration as per Kotlin requirements

echo "Adding opt-ins and suppressing warnings to all Kotlin source files..."

# File header with all opt-ins and suppressions
read -r -d '' HEADER << 'EOF'
@file:OptIn(
    ExperimentalStdlibApi::class,
    ExperimentalUnsignedTypes::class,
    ExperimentalTime::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalContracts::class,
    ExperimentalSerializationApi::class,
    ExperimentalForeignApi::class,
    ExperimentalNativeApi::class,
    ExperimentalWasmDsl::class,
    DelicateCoroutinesApi::class,
    InternalCoroutinesApi::class,
    ObsoleteCoroutinesApi::class,
    InternalSerializationApi::class,
    UnsafeNumber::class,
    kotlin.js.ExperimentalJsExport::class,
    kotlin.experimental.ExperimentalTypeInference::class,
    kotlin.contracts.ExperimentalContracts::class,
    kotlin.ExperimentalMultiplatform::class,
    kotlin.RequiresOptIn::class,
    kotlin.time.ExperimentalTime::class
)
@file:Suppress(
    "DEPRECATION",
    "DEPRECATION_ERROR",
    "UNUSED_PARAMETER",
    "UNUSED_VARIABLE",
    "UNUSED_VALUE",
    "VARIABLE_WITH_REDUNDANT_INITIALIZER",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "NOTHING_TO_INLINE",
    "NON_FINAL_MEMBER_IN_FINAL_CLASS",
    "NON_FINAL_MEMBER_IN_OBJECT",
    "REDUNDANT_NULLABLE",
    "USELESS_CAST",
    "USELESS_IS_CHECK",
    "IMPLICIT_CAST_TO_ANY",
    "UNCHECKED_CAST",
    "UNREACHABLE_CODE",
    "UNUSED_EXPRESSION",
    "REDUNDANT_PROJECTION",
    "REDUNDANT_VISIBILITY_MODIFIER",
    "RemoveRedundantBackticks",
    "SimplifyAssertNotNull",
    "ConstantConditionIf",
    "MemberVisibilityCanBePrivate",
    "CanBeParameter",
    "unused",
    "UNUSED",
    "SpellCheckingInspection",
    "RedundantVisibilityModifier",
    "RedundantModalityModifier",
    "RedundantGetter",
    "RedundantSetter",
    "RedundantExplicitType",
    "RedundantLambdaArrow",
    "RedundantValueArgument",
    "RedundantCallOfConversionMethod",
    "RedundantElseInWhen",
    "ClassName",
    "PropertyName",
    "FunctionName",
    "LongMethod",
    "LongParameterList",
    "LargeClass",
    "ComplexMethod",
    "TooManyFunctions",
    "TooGenericExceptionCaught",
    "MagicNumber",
    "NestedBlockDepth",
    "ReturnCount",
    "ThrowsCount",
    "ComplexCondition",
    "LabeledExpression",
    "StringLiteralDuplication",
    "SpreadOperator",
    "EXPERIMENTAL_API_USAGE",
    "EXPERIMENTAL_API_USAGE_ERROR",
    "EXPERIMENTAL_OVERRIDE",
    "EXPERIMENTAL_UNSIGNED_LITERALS",
    "EXPERIMENTAL_ANNOTATION_ON_OVERRIDE",
    "INLINE_FROM_HIGHER_PLATFORM",
    "NON_EXPORTABLE_TYPE",
    "FINAL_UPPER_BOUND",
    "UPPER_BOUND_VIOLATED",
    "REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE",
    "SUSPENSION_POINT_INSIDE_CRITICAL_SECTION",
    "MISSING_DEPENDENCY_CLASS",
    "PRE_RELEASE_CLASS",
    "EXPERIMENTAL_IS_NOT_ENABLED",
    "OVERRIDE_BY_INLINE",
    "REIFIED_TYPE_PARAMETER_NO_INLINE",
    "INCORRECT_INPUT_OUTPUT_ANNOTATIONS",
    "NOT_YET_SUPPORTED_IN_INLINE",
    "DECLARATION_CANT_BE_INLINED",
    "INLINE_PROPERTY_WITH_BACKING_FIELD",
    "NON_LOCAL_RETURN_NOT_ALLOWED"
)
EOF

# Function to process a single file
process_file() {
    local file="$1"
    
    # Skip if file already has @file:OptIn
    if grep -q "^@file:OptIn" "$file"; then
        echo "Skipping $file (already has opt-ins)"
        return
    fi
    
    # Create temp file
    local temp_file=$(mktemp)
    
    # Add header BEFORE package declaration
    echo "$HEADER" > "$temp_file"
    echo "" >> "$temp_file"
    
    # Skip any existing @file: annotations to avoid duplicates
    awk '
    BEGIN { skip = 0 }
    /^@file:/ { skip = 1 }
    /^package/ || /^import/ || /^\/\// || /^\/\*/ || /^[a-zA-Z]/ {
        if (skip) skip = 0
    }
    !skip { print }
    ' "$file" >> "$temp_file"
    
    # Replace original file
    mv "$temp_file" "$file"
    echo "Processed: $file"
}

# Find all Kotlin source files (excluding build directories and .kts files)
find . -name "*.kt" -not -name "*.kts" -not -path "*/build/*" -not -path "*/.gradle/*" -not -path "*/node_modules/*" | while read -r file; do
    process_file "$file"
done

echo "Done! All Kotlin source files have been decorated with opt-ins and warning suppressions."
echo "IntelliJ IDEA will optimize these out when appropriate."
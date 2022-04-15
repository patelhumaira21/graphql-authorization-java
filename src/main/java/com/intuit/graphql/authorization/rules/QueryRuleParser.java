package com.intuit.graphql.authorization.rules;

import static com.intuit.graphql.authorization.util.GraphQLUtil.isIntrospection_Field;
import static com.intuit.graphql.authorization.util.GraphQLUtil.isNotEmpty;

import com.intuit.graphql.authorization.util.GraphQLUtil;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.parser.Parser;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnmodifiedType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QueryRuleParser implements RuleParser {

  private static final String ERR_MSG = "Unknown field '%s'";

  private final GraphQLSchema schema;

  public QueryRuleParser(GraphQLSchema schema) {
    this.schema = Objects.requireNonNull(schema);
  }

  private void preOrder(GraphQLType graphQLOutputType, SelectionSet selectionSet,
      Map<GraphQLType, Set<GraphQLFieldDefinition>> typeToFieldMap) {
    final GraphQLUnmodifiedType graphQLUnmodifiedType = GraphQLTypeUtil.unwrapAll(graphQLOutputType);
    if (graphQLUnmodifiedType instanceof GraphQLFieldsContainer && isNotEmpty(selectionSet)) {
      GraphQLFieldsContainer graphQLFieldsContainer = (GraphQLFieldsContainer) graphQLUnmodifiedType;
      selectionSet.getSelections()
          .forEach(node -> {
            if (node instanceof Field) {
              Field field = (Field) node;
              if (!isIntrospection_Field(field)) {
                final GraphQLFieldDefinition fieldDefinition = graphQLFieldsContainer.getFieldDefinition(field.getName());
                if (fieldDefinition == null) {
                  throw new IllegalStateException(String.format(ERR_MSG, field.getName()));
                }
                Set<GraphQLFieldDefinition> fields = typeToFieldMap
                    .computeIfAbsent(graphQLOutputType, k -> new HashSet<>());
                fields.add(fieldDefinition);
                preOrder(fieldDefinition.getType(), field.getSelectionSet(), typeToFieldMap);
              }
            }
          });
    }
  }

  @Override
  public Map<GraphQLType, Set<GraphQLFieldDefinition>> parseRule(final String query) {
    Map<GraphQLType, Set<GraphQLFieldDefinition>> typeToFieldMap = new HashMap<>();
    Document document = new Parser().parseDocument(query);
    document.getDefinitions()
        .forEach(definition -> {
          if (definition instanceof OperationDefinition) {
            OperationDefinition operationDefinition = (OperationDefinition) definition;
            GraphQLOutputType operationType = GraphQLUtil.getRootTypeFromOperation(operationDefinition, schema);
            preOrder(operationType, operationDefinition.getSelectionSet(), typeToFieldMap);
          }
        });
    return typeToFieldMap;
  }

}

package com.linkedin.datahub.graphql.resolvers.search;

import com.google.common.collect.ImmutableList;
import com.linkedin.datahub.graphql.exception.ValidationException;
import com.linkedin.datahub.graphql.generated.EntityType;
import com.linkedin.datahub.graphql.generated.SearchAcrossEntitiesInput;
import com.linkedin.datahub.graphql.generated.SearchResults;
import com.linkedin.datahub.graphql.resolvers.EntityTypeMapper;
import com.linkedin.datahub.graphql.resolvers.ResolverUtils;
import com.linkedin.datahub.graphql.types.mappers.UrnSearchResultsMapper;
import com.linkedin.entity.client.RestliEntityClient;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.linkedin.datahub.graphql.resolvers.ResolverUtils.bindArgument;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Resolver responsible for resolving 'searchAcrossEntities' field of the Query type
 */
@Slf4j
@RequiredArgsConstructor
public class SearchAcrossEntitiesResolver implements DataFetcher<CompletableFuture<SearchResults>> {

  private static final int DEFAULT_START = 0;
  private static final int DEFAULT_COUNT = 10;

  private static final List<EntityType> SEARCHABLE_ENTITY_TYPES =
      ImmutableList.of(EntityType.DATASET, EntityType.DASHBOARD, EntityType.CHART, EntityType.MLMODEL,
          EntityType.MLMODEL_GROUP, EntityType.MLFEATURE_TABLE, EntityType.DATA_FLOW, EntityType.DATA_JOB,
          EntityType.GLOSSARY_TERM, EntityType.TAG, EntityType.CORP_USER, EntityType.CORP_GROUP);

  private final RestliEntityClient _entityClient;

  @Override
  public CompletableFuture<SearchResults> get(DataFetchingEnvironment environment) {
    final SearchAcrossEntitiesInput input =
        bindArgument(environment.getArgument("input"), SearchAcrossEntitiesInput.class);

    List<EntityType> entityTypes =
        (input.getTypes() == null || input.getTypes().isEmpty()) ? SEARCHABLE_ENTITY_TYPES : input.getTypes();
    List<String> entityNames = entityTypes.stream().map(EntityTypeMapper::getName).collect(Collectors.toList());

    // escape forward slash since it is a reserved character in Elasticsearch
    final String sanitizedQuery = ResolverUtils.escapeForwardSlash(input.getQuery());
    if (isBlank(sanitizedQuery)) {
      log.error("'query' parameter cannot was null or empty");
      throw new ValidationException("'query' parameter cannot be null or empty");
    }

    final int start = input.getStart() != null ? input.getStart() : DEFAULT_START;
    final int count = input.getCount() != null ? input.getCount() : DEFAULT_COUNT;

    return CompletableFuture.supplyAsync(() -> {
      try {
        log.debug(
            "Executing search for multiple entities: entity types {}, query {}, filters: {}, start: {}, count: {}",
            input.getTypes(), input.getQuery(), input.getFilters(), start, count);
        return UrnSearchResultsMapper.map(_entityClient.searchAcrossEntities(entityNames, sanitizedQuery,
            ResolverUtils.buildFilter(input.getFilters()), start, count, ResolverUtils.getActor(environment)));
      } catch (Exception e) {
        log.error(
            "Failed to execute search for multiple entities: entity types {}, query {}, filters: {}, start: {}, count: {}",
            input.getTypes(), input.getQuery(), input.getFilters(), start, count);
        throw new RuntimeException(
            "Failed to execute search: " + String.format("entity types %s, query %s, filters: %s, start: %s, count: %s",
                input.getTypes(), input.getQuery(), input.getFilters(), start, count), e);
      }
    });
  }
}

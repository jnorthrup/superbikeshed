# flatton TODO

## Architecture Update

- [x] Replace crude SimdJsonScanner with BitmapJsonDecoder implementation
- [x] Remove fragile regex-based JSON parsing
- [x] Implement robust JsonObjectCursor with bitmap scanning
- [x] Replace JsonWireProtoAdapter with production-ready version

## Integration Testing

- [ ] Verify FlattonService.queryViewAsCursor uses new implementation
- [ ] Test CouchDB view response parsing accuracy
- [ ] Benchmark performance vs previous implementation
- [ ] Validate cursor positioning and navigation

## API Refinement

- [ ] Add error handling for malformed JSON
- [ ] Implement streaming cursor for large datasets
- [ ] Add support for nested object navigation
- [ ] Optimize memory usage for large JSON documents

## Documentation

- [ ] Update API documentation for new cursor interface
- [ ] Add performance benchmarks
- [ ] Create usage examples for common patterns
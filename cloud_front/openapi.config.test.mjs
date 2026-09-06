import test from 'node:test';
import assert from 'node:assert/strict';
import { buildOpenApiAuthorization, openApiConfig } from './openapi.config.js';

test('buildOpenApiAuthorization returns a Basic authorization header', () => {
  const authorization = buildOpenApiAuthorization({
    DOC_USERNAME: 'admin',
    DOC_PASSWORD: 'secret',
  });

  assert.equal(authorization, 'Basic YWRtaW46c2VjcmV0');
});

test('buildOpenApiAuthorization uses the documented local username default', () => {
  const authorization = buildOpenApiAuthorization({
    DOC_PASSWORD: 'secret',
  });

  assert.equal(authorization, 'Basic YWRtaW46c2VjcmV0');
});

test('OpenAPI int64 fields stay safe for snowflake ids', () => {
  const customType = openApiConfig.hook?.customType;

  assert.equal(
    customType?.({ type: 'integer', format: 'int64' }, 'API', () => 'number'),
    'string | number',
  );
  assert.equal(customType?.({ type: 'integer', format: 'int32' }, 'API', () => 'number'), 'number');
});

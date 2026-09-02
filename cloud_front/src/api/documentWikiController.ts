// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** addDocumentWiki POST /api/documentWiki/add */
export async function addDocumentWikiUsingPost(
  body: API.DocumentWikiAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong_>('/api/documentWiki/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** deleteDocumentWiki POST /api/documentWiki/delete */
export async function deleteDocumentWikiUsingPost(
  body: API.DeleteRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/documentWiki/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** editDocumentWiki POST /api/documentWiki/edit */
export async function editDocumentWikiUsingPost(
  body: API.DocumentWikiEditRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/documentWiki/edit', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** getDocumentWikiVisById GET /api/documentWiki/get/vis */
export async function getDocumentWikiVisByIdUsingGet(
  params: API.getDocumentWikiVisByIdUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseDocumentWikiVis_>('/api/documentWiki/get/vis', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** listDocumentWikiVisByPage POST /api/documentWiki/list/page/vis */
export async function listDocumentWikiVisByPageUsingPost(
  body: API.DocumentWikiQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageDocumentWikiVis_>('/api/documentWiki/list/page/vis', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** listDocumentWikiVisByPageWithCache POST /api/documentWiki/list/page/vis/cache */
export async function listDocumentWikiVisByPageWithCacheUsingPost(
  body: API.DocumentWikiQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageDocumentWikiVis_>('/api/documentWiki/list/page/vis/cache', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** listRecycle GET /api/wikiRecycle/list */
export async function listRecycleUsingGet(
  params: API.listRecycleUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListWikiRecycleItemVis_>('/api/wikiRecycle/list', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** restoreRecycleItem POST /api/wikiRecycle/restore */
export async function restoreRecycleItemUsingPost(
  body: API.WikiRecycleActionRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/wikiRecycle/restore', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** permanentDeleteRecycleItem POST /api/wikiRecycle/permanentDelete */
export async function permanentDeleteRecycleItemUsingPost(
  body: API.WikiRecycleActionRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/wikiRecycle/permanentDelete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

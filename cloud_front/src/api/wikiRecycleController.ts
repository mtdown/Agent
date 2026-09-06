// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** list GET /api/wikiRecycle/list */
export async function listUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listUsingGETParams,
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

/** permanentDelete POST /api/wikiRecycle/permanentDelete */
export async function permanentDeleteUsingPost(
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

/** restore POST /api/wikiRecycle/restore */
export async function restoreUsingPost(
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

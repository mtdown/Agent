// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** addFolder POST /api/wikiFolder/add */
export async function addFolderUsingPost(
  body: API.WikiFolderAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong_>('/api/wikiFolder/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** renameFolder POST /api/wikiFolder/rename */
export async function renameFolderUsingPost(
  body: API.WikiFolderRenameRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/wikiFolder/rename', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** moveFolder POST /api/wikiFolder/move */
export async function moveFolderUsingPost(
  body: API.WikiFolderMoveRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/wikiFolder/move', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** deleteFolder POST /api/wikiFolder/delete */
export async function deleteFolderUsingPost(
  body: API.DeleteRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/wikiFolder/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** listFolderTree GET /api/wikiFolder/tree/list */
export async function listFolderTreeUsingGet(
  params: API.listFolderTreeUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListWikiFolderVis_>('/api/wikiFolder/tree/list', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

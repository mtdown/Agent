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
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
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

/** importDocumentWiki POST /api/documentWiki/import */
export async function importDocumentWikiUsingPost(
  params: API.importDocumentWikiUsingPOSTParams,
  body: {},
  file?: File,
  options?: { [key: string]: any },
) {
  const formData = new FormData()

  if (file) {
    formData.append('file', file)
  }

  Object.keys(body).forEach((ele) => {
    const item = (body as any)[ele]

    if (item !== undefined && item !== null) {
      if (typeof item === 'object' && !(item instanceof File)) {
        if (item instanceof Array) {
          item.forEach((f) => formData.append(ele, f || ''))
        } else {
          formData.append(ele, new Blob([JSON.stringify(item)], { type: 'application/json' }))
        }
      } else {
        formData.append(ele, item)
      }
    }
  })

  return request<API.BaseResponseLong_>('/api/documentWiki/import', {
    method: 'POST',
    params: {
      ...params,
    },
    data: formData,
    ...(options || {}),
  })
}

/** uploadWikiImage POST /api/documentWiki/image/upload */
export async function uploadWikiImageUsingPost(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.uploadWikiImageUsingPOSTParams,
  body: {},
  file?: File,
  options?: { [key: string]: any }
) {
  const formData = new FormData()

  if (file) {
    formData.append('file', file)
  }

  Object.keys(body).forEach((ele) => {
    const item = (body as any)[ele]

    if (item !== undefined && item !== null) {
      if (typeof item === 'object' && !(item instanceof File)) {
        if (item instanceof Array) {
          item.forEach((f) => formData.append(ele, f || ''))
        } else {
          formData.append(ele, new Blob([JSON.stringify(item)], { type: 'application/json' }))
        }
      } else {
        formData.append(ele, item)
      }
    }
  })

  return request<API.BaseResponseString_>('/api/documentWiki/image/upload', {
    method: 'POST',
    params: {
      ...params,
    },
    data: formData,
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

/** moveDocumentWiki POST /api/documentWiki/move */
export async function moveDocumentWikiUsingPost(
  body: API.DocumentWikiMoveRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/documentWiki/move', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** listRootDocumentWiki GET /api/documentWiki/root/list */
export async function listRootDocumentWikiUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listRootDocumentWikiUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListDocumentWikiVis_>('/api/documentWiki/root/list', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

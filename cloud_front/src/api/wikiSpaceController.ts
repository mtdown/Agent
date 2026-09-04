// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** listVisibleSpace GET /api/wikiSpace/list/visible */
export async function listVisibleSpaceUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseListWikiSpaceVis_>('/api/wikiSpace/list/visible', {
    method: 'GET',
    ...(options || {}),
  })
}

/** addTeamSpace POST /api/wikiSpace/add/team */
export async function addTeamSpaceUsingPost(
  body: API.WikiTeamSpaceAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong_>('/api/wikiSpace/add/team', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** listManageTeamSpaces GET /api/wikiSpace/list/manage/team */
export async function listManageTeamSpacesUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseListWikiSpaceVis_>('/api/wikiSpace/list/manage/team', {
    method: 'GET',
    ...(options || {}),
  })
}

/** addTeamMember POST /api/wikiSpace/member/add */
export async function addTeamMemberUsingPost(
  body: API.WikiTeamMemberAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong_>('/api/wikiSpace/member/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** removeTeamMember POST /api/wikiSpace/member/delete */
export async function removeTeamMemberUsingPost(
  body: API.WikiTeamMemberDeleteRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/wikiSpace/member/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** listTeamMembers GET /api/wikiSpace/member/list */
export async function listTeamMembersUsingGet(
  params: API.listTeamMembersUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListWikiSpaceUserVis_>('/api/wikiSpace/member/list', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** exitTeamSpace POST /api/wikiSpace/exit */
export async function exitTeamSpaceUsingPost(
  body: API.WikiSpaceConfirmRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/wikiSpace/exit', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** deleteTeamSpace POST /api/wikiSpace/delete/team */
export async function deleteTeamSpaceUsingPost(
  body: API.WikiSpaceConfirmRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/wikiSpace/delete/team', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** restoreTeamSpace POST /api/wikiSpace/restore/team */
export async function restoreTeamSpaceUsingPost(
  body: API.WikiSpaceConfirmRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/wikiSpace/restore/team', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** permanentDeleteTeamSpace POST /api/wikiSpace/permanentDelete/team */
export async function permanentDeleteTeamSpaceUsingPost(
  body: API.WikiSpaceConfirmRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/wikiSpace/permanentDelete/team', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

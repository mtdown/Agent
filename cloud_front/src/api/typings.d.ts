declare namespace API {
  type BaseResponseBoolean_ = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseDocumentWikiVis_ = {
    code?: number
    data?: DocumentWikiVis
    message?: string
  }

  type BaseResponseListDocumentWikiVis_ = {
    code?: number
    data?: DocumentWikiVis[]
    message?: string
  }

  type BaseResponseCreateOutPaintingTaskResponse_ = {
    code?: number
    data?: CreateOutPaintingTaskResponse
    message?: string
  }

  type BaseResponseGetOutPaintingTaskResponse_ = {
    code?: number
    data?: GetOutPaintingTaskResponse
    message?: string
  }

  type BaseResponseInt_ = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponseListSpaceLevel_ = {
    code?: number
    data?: SpaceLevel[]
    message?: string
  }

  type BaseResponseListSpaceUserVis_ = {
    code?: number
    data?: SpaceUserVis[]
    message?: string
  }

  type BaseResponseListWikiFolderVis_ = {
    code?: number
    data?: WikiFolderVis[]
    message?: string
  }

  type BaseResponseListWikiRecycleItemVis_ = {
    code?: number
    data?: WikiRecycleItemVis[]
    message?: string
  }

  type BaseResponseListWikiSpaceUserVis_ = {
    code?: number
    data?: WikiSpaceUserVis[]
    message?: string
  }

  type BaseResponseListWikiSpaceVis_ = {
    code?: number
    data?: WikiSpaceVis[]
    message?: string
  }

  type BaseResponseLoginUserVis_ = {
    code?: number
    data?: LoginUserVis
    message?: string
  }

  type BaseResponseLong_ = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponsePagePicture_ = {
    code?: number
    data?: PagePicture_
    message?: string
  }

  type BaseResponsePagePictureVis_ = {
    code?: number
    data?: PagePictureVis_
    message?: string
  }

  type BaseResponsePageDocumentWikiVis_ = {
    code?: number
    data?: PageDocumentWikiVis_
    message?: string
  }

  type BaseResponsePageSpace_ = {
    code?: number
    data?: PageSpace_
    message?: string
  }

  type BaseResponsePageSpaceVis_ = {
    code?: number
    data?: PageSpaceVis_
    message?: string
  }

  type BaseResponsePageUserVis_ = {
    code?: number
    data?: PageUserVis_
    message?: string
  }

  type BaseResponsePicture_ = {
    code?: number
    data?: Picture
    message?: string
  }

  type BaseResponsePictureTagCategory_ = {
    code?: number
    data?: PictureTagCategory
    message?: string
  }

  type BaseResponsePictureVis_ = {
    code?: number
    data?: PictureVis
    message?: string
  }

  type BaseResponseSpace_ = {
    code?: number
    data?: Space
    message?: string
  }

  type BaseResponseSpaceUser_ = {
    code?: number
    data?: SpaceUser
    message?: string
  }

  type BaseResponseSpaceVis_ = {
    code?: number
    data?: SpaceVis
    message?: string
  }

  type BaseResponseString_ = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser_ = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVis_ = {
    code?: number
    data?: UserVis
    message?: string
  }

  type CreateOutPaintingTaskResponse = {
    code?: string
    message?: string
    output?: Output
    requestId?: string
  }

  type CreatePictureOutPaintingTaskRequest = {
    parameters?: Parameters
    pictureId?: number
  }

  type DeleteRequest = {
    id?: string | number
  }

  type DocumentWikiAddRequest = {
    content?: string
    folderId?: string | number
    spaceId?: string | number
    summary?: string
    tags?: string[]
    title?: string
  }

  type DocumentWikiEditRequest = {
    content?: string
    folderId?: string | number
    id?: string
    spaceId?: string | number
    summary?: string
    tags?: string[]
    title?: string
  }

  type DocumentWikiMoveRequest = {
    id?: string | number
    targetFolderId?: string | number
    targetSpaceId?: string | number
  }

  type DocumentWikiQueryRequest = {
    folderId?: string | number
    current?: number
    id?: string
    matchMode?: string
    pageSize?: number
    searchText?: string
    sortField?: string
    sortOrder?: string
    spaceId?: string | number
    summary?: string
    tags?: string[]
    title?: string
    userId?: number
  }

  type DocumentWikiVis = {
    content?: string
    createTime?: string
    deleteBy?: string | number
    deleteTime?: string
    editTime?: string
    folderId?: string | number
    id?: string
    spaceId?: string | number
    summary?: string
    tags?: string[]
    title?: string
    updateTime?: string
    user?: UserVis
    userId?: number
    viewCount?: number
  }

  type GetOutPaintingTaskResponse = {
    output?: Output1
    requestId?: string
  }

  type getPictureByIdUsingGETParams = {
    /** id */
    id?: number
  }

  type getDocumentWikiVisByIdUsingGETParams = {
    /** id */
    id?: string
  }

  type listFolderTreeUsingGETParams = {
    spaceId?: string | number
  }

  type listRecycleUsingGETParams = {
    spaceId?: string | number
  }

  type listRootDocumentWikiUsingGETParams = {
    spaceId?: string | number
  }

  type listTeamMembersUsingGETParams = {
    spaceId?: string | number
  }

  type getPictureOutPaintingTaskUsingGETParams = {
    /** taskId */
    taskId?: string
  }

  type getPictureVisByIdUsingGETParams = {
    /** id */
    id?: number
  }

  type getSpaceByIdUsingGETParams = {
    /** id */
    id?: number
  }

  type getSpaceVisByIdUsingGETParams = {
    /** id */
    id?: number
  }

  type getUserByIdUsingGETParams = {
    /** id */
    id?: number
  }

  type getUserVisByIdUsingGETParams = {
    /** id */
    id?: number
  }

  type LoginUserVis = {
    createTime?: string
    id?: number
    updateTime?: string
    userAccount?: string
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  type Output = {
    taskId?: string
    taskStatus?: string
  }

  type Output1 = {
    code?: string
    endTime?: string
    message?: string
    outputImageUrl?: string
    scheduledTime?: string
    submitTime?: string
    taskId?: string
    taskMetrics?: TaskMetrics
    taskStatus?: string
  }

  type PagePicture_ = {
    current?: number
    pages?: number
    records?: Picture[]
    size?: number
    total?: number
  }

  type PageDocumentWikiVis_ = {
    current?: number
    pages?: number
    records?: DocumentWikiVis[]
    size?: number
    total?: number
  }

  type PagePictureVis_ = {
    current?: number
    pages?: number
    records?: PictureVis[]
    size?: number
    total?: number
  }

  type PageSpace_ = {
    current?: number
    pages?: number
    records?: Space[]
    size?: number
    total?: number
  }

  type PageSpaceVis_ = {
    current?: number
    pages?: number
    records?: SpaceVis[]
    size?: number
    total?: number
  }

  type PageUserVis_ = {
    current?: number
    pages?: number
    records?: UserVis[]
    size?: number
    total?: number
  }

  type Parameters = {
    addWatermark?: boolean
    angle?: number
    bestQuality?: boolean
    bottomOffset?: number
    leftOffset?: number
    limitImageSize?: boolean
    outputRatio?: string
    rightOffset?: number
    topOffset?: number
    xScale?: number
    yScale?: number
  }

  type Picture = {
    category?: string
    createTime?: string
    editTime?: string
    id?: number
    introduction?: string
    isDelete?: number
    name?: string
    picFormat?: string
    picHeight?: number
    picScale?: number
    picSize?: number
    picWidth?: number
    reviewMessage?: string
    reviewStatus?: number
    reviewTime?: string
    reviewerId?: number
    spaceId?: number
    tags?: string
    thumbnailUrl?: string
    updateTime?: string
    url?: string
    userId?: number
  }

  type PictureEditRequest = {
    category?: string
    id?: number
    introduction?: string
    name?: string
    tags?: string[]
  }

  type PictureQueryRequest = {
    category?: string
    current?: number
    id?: number
    introduction?: string
    name?: string
    nullSpaceId?: boolean
    pageSize?: number
    picFormat?: string
    picHeight?: number
    picScale?: number
    picSize?: number
    picWidth?: number
    reviewMessage?: string
    reviewStatus?: number
    reviewerId?: number
    reviewerTime?: string
    searchText?: string
    sortField?: string
    sortOrder?: string
    spaceId?: number
    tags?: string[]
    userId?: number
  }

  type PictureReviewRequest = {
    id?: number
    reviewMessage?: string
    reviewStatus?: number
  }

  type PictureTagCategory = {
    categoryList?: string[]
    tagList?: string[]
  }

  type PictureUpdateRequest = {
    category?: string
    id?: number
    introduction?: string
    name?: string
    tags?: string[]
  }

  type PictureUploadByBatchRequest = {
    count?: number
    namePrefix?: string
    searchText?: string
  }

  type PictureUploadRequest = {
    fileUrl?: string
    id?: number
    picName?: string
    spaceId?: number
  }

  type PictureVis = {
    category?: string
    createTime?: string
    editTime?: string
    id?: number
    introduction?: string
    name?: string
    permissionList?: string[]
    picFormat?: string
    picHeight?: number
    picScale?: number
    picSize?: number
    picWidth?: number
    spaceId?: number
    tags?: string[]
    thumbnailUrl?: string
    updateTime?: string
    url?: string
    user?: UserVis
    userId?: number
  }

  type Space = {
    createTime?: string
    editTime?: string
    id?: number
    isDelete?: number
    maxCount?: number
    maxSize?: number
    spaceLevel?: number
    spaceName?: string
    spaceType?: number
    totalCount?: number
    totalSize?: number
    updateTime?: string
    userId?: number
  }

  type SpaceAddRequest = {
    spaceLevel?: number
    spaceName?: string
    spaceType?: number
  }

  type SpaceEditRequest = {
    id?: number
    spaceName?: string
  }

  type SpaceLevel = {
    maxCount?: number
    maxSize?: number
    text?: string
    value?: number
  }

  type SpaceQueryRequest = {
    current?: number
    id?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    spaceLevel?: number
    spaceName?: string
    spaceType?: number
    userId?: number
  }

  type SpaceUpdateRequest = {
    id?: number
    maxCount?: number
    maxSize?: number
    spaceLevel?: number
    spaceName?: string
  }

  type SpaceUser = {
    createTime?: string
    id?: number
    spaceId?: number
    spaceRole?: string
    updateTime?: string
    userId?: number
  }

  type SpaceUserAddRequest = {
    spaceId?: number
    spaceRole?: string
    userId?: number
  }

  type SpaceUserEditRequest = {
    id?: number
    spaceRole?: string
  }

  type SpaceUserQueryRequest = {
    id?: number
    spaceId?: number
    spaceRole?: string
    userId?: number
  }

  type SpaceUserVis = {
    createTime?: string
    id?: number
    space?: SpaceVis
    spaceId?: number
    spaceRole?: string
    updateTime?: string
    user?: UserVis
    userId?: number
  }

  type SpaceVis = {
    createTime?: string
    editTime?: string
    id?: number
    maxCount?: number
    maxSize?: number
    permissionList?: string[]
    spaceLevel?: number
    spaceName?: string
    spaceType?: number
    totalCount?: number
    totalSize?: number
    updateTime?: string
    user?: UserVis
    userId?: number
  }

  type TaskMetrics = {
    failed?: number
    succeeded?: number
    total?: number
  }

  type testDownloadFileUsingGETParams = {
    /** filepath */
    filepath?: string
  }

  type uploadPictureUsingPOSTParams = {
    fileUrl?: string
    id?: number
    picName?: string
    spaceId?: number
  }

  type User = {
    createTime?: string
    editTime?: string
    id?: number
    isDelete?: number
    updateTime?: string
    userAccount?: string
    userAvatar?: string
    userName?: string
    userPassword?: string
    userProfile?: string
    userRole?: string
  }

  type UserAddRequest = {
    userAccount?: string
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserQueryRequest = {
    current?: number
    id?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    userAccount?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    checkPassword?: string
    userAccount?: string
    userPassword?: string
  }

  type UserUpdateRequest = {
    id?: number
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  type UserVis = {
    createTime?: string
    id?: number
    userAccount?: string
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  type WikiFolderAddRequest = {
    name?: string
    parentId?: string | number
    spaceId?: string | number
  }

  type WikiFolderMoveRequest = {
    id?: string | number
    parentId?: string | number
  }

  type WikiFolderRenameRequest = {
    id?: string | number
    name?: string
  }

  type WikiFolderVis = {
    children?: WikiFolderVis[]
    createTime?: string
    deleteBy?: string | number
    deleteTime?: string
    deleteUser?: UserVis
    documents?: DocumentWikiVis[]
    editTime?: string
    id?: string | number
    name?: string
    parentId?: string | number
    spaceId?: string | number
    updateTime?: string
  }

  type WikiRecycleActionRequest = {
    confirm?: boolean
    itemId?: string | number
    itemType?: string
    spaceId?: string | number
  }

  type WikiRecycleItemVis = {
    deleteBy?: string | number
    deleteTime?: string
    deleteUser?: UserVis
    itemId?: string | number
    itemType?: string
    parentId?: string | number
    spaceId?: string | number
    title?: string
  }

  type WikiSpaceConfirmRequest = {
    confirm?: boolean
    id?: string | number
  }

  type WikiSpaceUserVis = {
    createTime?: string
    id?: string | number
    spaceId?: string | number
    spaceRole?: string
    updateTime?: string
    user?: UserVis
    userId?: string | number
  }

  type WikiSpaceVis = {
    createTime?: string
    id?: string | number
    isDelete?: number
    name?: string
    ownerUserId?: string | number
    type?: number
    updateTime?: string
  }

  type WikiTeamMemberAddRequest = {
    spaceId?: string | number
    spaceRole?: string
    userId?: string | number
  }

  type WikiTeamMemberDeleteRequest = {
    spaceId?: string | number
    userId?: string | number
  }

  type WikiTeamSpaceAddRequest = {
    name?: string
  }
}

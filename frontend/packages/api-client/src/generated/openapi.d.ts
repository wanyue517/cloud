/* eslint-disable */
// AUTO-GENERATED from docs/api/openapi.json by packages/api-client/scripts/generate-types.mjs
// Do not edit manually. Run `pnpm api:generate` after backend API contract changes.

export type ResourceId = string

export interface components {
  schemas: {
    AccountController_ChangePasswordInput: {
      currentPassword?: string
      newPassword?: string
    }
    AccountController_ProfileInput: {
      displayName?: string
      avatarUrl?: string
    }
    AccountController_VerifyPasswordInput: {
      password?: string
    }
    AdminController_AuthAndSmtpInput: {
      allowRegister: boolean
      requireEmailVerify: boolean
      allowPasswordLogin: boolean
      smtpHost?: string
      smtpPort: number
      smtpUsername?: string
      smtpFrom?: string
      smtpPassword?: string
    }
    AdminController_FilePolicyInput: {
      maxFileVersions: number
      uploadChunkSizeMb?: number
      uploadMaxFileMb?: number
      uploadMaxConcurrentSessions?: number
      uploadSessionTtlHours?: number
      userDefaultQuotaGb?: number
      maxDirectoryDepth?: number
      trashRetentionDays?: number
      blockedExtensions?: string
    }
    AdminController_OAuthProviderInput: {
      enabled: boolean
      clientId?: string
      clientSecret?: string
      authorizeUrl?: string
      tokenUrl?: string
    }
    AdminController_SiteSettingsInput: {
      siteName?: string
      siteLogoUrl?: string
      siteAnnouncement?: string
    }
    AdminController_Status: {
      status: number
    }
    AdminController_StorageProfileInput: {
      playbackMode?: "AUTO" | "DIRECT" | "PROXY"
      playbackEndpoint?: string
      name?: string
      provider?: string
      rootPath?: string
      endpoint?: string
      bucket?: string
      region?: string
      accessKey?: string
      secretKey?: string
      maxFileBytes?: number
      uploadMode?: string
      directThresholdBytes?: number
      directChunkSize?: number
      directConcurrency?: number
      directUrlTtl?: number
      directFallback?: boolean
    }
    AdminController_StorageProfileTestInput: {
      id?: string
      provider?: string
      rootPath?: string
      endpoint?: string
      bucket?: string
      region?: string
      accessKey?: string
      secretKey?: string
    }
    AdminController_StorageRoutingInput: {
      userFilesProfileId?: string
      siteAssetsProfileId?: string
      userAvatarsProfileId?: string
    }
    ApiResponse: {
      code: number
      traceId?: string | null
      msg: string
      data?: null
    }
    AuthController_ResetPasswordReq: {
      email?: string
      code?: string
      newPassword?: string
    }
    Consumer: Record<string, unknown>
    DriveModels_ChangeInput: {
      name?: string
      parentId: string
      revision: number
    }
    DriveModels_DirectoryStats: {
      folders: number
      files: number
      categories?: Record<string, number>
    }
    DriveModels_Node: {
      id?: string
      spaceId?: string
      parentId?: string
      name?: string
      nodeType?: string
      currentVersionId?: string
      revision: number
      size: number
      mimeType?: string
      hidden: boolean
      updatedAt?: string
      hasChildren: boolean
    }
    DriveModels_NodeInput: {
      name?: string
      parentId: string
    }
    DriveModels_Page: {
      items?: Array<components['schemas']["DriveModels_Node"]>
      total: number
      page: number
      pageSize: number
    }
    DriveModels_Space: {
      id?: string
      name?: string
      kind?: string
      icon?: string
      color?: string
      description?: string
      sortOrder: number
      role?: string
      ownerName?: string
    }
    DriveModels_SpaceInput: {
      name?: string
      description?: string
      color?: string
      sortOrder?: number
      icon?: string
      kind?: string
    }
    DriveModels_TextChunk: {
      content?: string
      offset: number
      bytesRead: number
      totalBytes: number
      hasMore: boolean
      binaryDetected: boolean
    }
    DriveModels_TransferInput: {
      targetSpaceId: string
      targetParentId: string
      mode?: string
      revision: number
    }
    DriveModels_Upload: {
      id?: string
      chunkSize: number
      chunkCount: number
      received?: Array<number>
      missing?: Array<number>
      status?: string
      fileId?: string
      errorMessage?: string
      instantHit: boolean
      uploadMode?: string
    }
    DriveModels_UploadInput: {
      spaceId: string
      parentId: string
      name?: string
      size: number
      mimeType?: string
      expectedSha256?: string
    }
    DriveModels_Version: {
      id?: string
      number: number
      size: number
      mimeType?: string
      createdAt?: string
      createdByName?: string
    }
    EmailLoginReqVO: {
      email: string
      password: string
    }
    EmailRegisterReqVO: {
      email: string
      code: string
      password: string
    }
    Lock: Record<string, unknown>
    LoginLogCreateReqVO: {
      userId?: string
      sessionId?: string
      loginType?: string
      identifierMasked?: string
      success?: boolean
      failureReason?: string
      ipAddress?: string
      userAgent?: string
      clientType?: string
      occurredAt?: string
    }
    LoginLogRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      userId?: string
      sessionId?: string
      loginType?: string
      identifierMasked?: string
      success?: boolean
      failureReason?: string
      ipAddress?: string
      userAgent?: string
      clientType?: string
      occurredAt?: string
    }
    LoginLogUpdateReqVO: {
      version: number
      userId?: string
      sessionId?: string
      loginType?: string
      identifierMasked?: string
      success?: boolean
      failureReason?: string
      ipAddress?: string
      userAgent?: string
      clientType?: string
      occurredAt?: string
    }
    MediaTimelineService_TimelineItem: {
      id?: string
      spaceId?: string
      spaceName?: string
      parentId?: string
      name?: string
      size: number
      mimeType?: string
      mediaType?: string
      takenAt?: string
      dateGroup?: string
      width?: number
      height?: number
      durationSeconds?: number
      currentVersionId?: string
      revision: number
    }
    MediaTimelineService_TimelinePage: {
      items?: Array<components['schemas']["MediaTimelineService_TimelineItem"]>
      total: number
      page: number
      pageSize: number
      hasMore: boolean
    }
    MediaType: {
      type?: string
      subtype?: string
      parameters?: Record<string, string>
      toStringValue?: string
      hash: number
    }
    MemberCreateReqVO: {
      spaceId?: string
      userId?: string
      role?: string
      joinedAt?: string
      expiresAt?: string
      ownerSpaceId?: string
    }
    MemberRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      spaceId?: string
      userId?: string
      role?: string
      joinedAt?: string
      expiresAt?: string
      ownerSpaceId?: string
    }
    MemberUpdateReqVO: {
      version: number
      spaceId?: string
      userId?: string
      role?: string
      joinedAt?: string
      expiresAt?: string
      ownerSpaceId?: string
    }
    MessageModels_AdminMessageRecord: {
      id?: string
      scope?: string
      category?: string
      title?: string
      content?: string
      actionUrl?: string
      recipientCount: number
      readCount: number
      createdAt?: string
    }
    MessageModels_InboxResponse: {
      unreadCount: number
      items?: Array<components['schemas']["MessageModels_MessageItem"]>
    }
    MessageModels_MessageItem: {
      id?: string
      category?: string
      scope?: string
      title?: string
      content?: string
      actionUrl?: string
      read: boolean
      createdAt?: string
    }
    MessageModels_PublishMessageInput: {
      scope?: string
      target?: string
      title?: string
      content?: string
      actionUrl?: string
    }
    MfaFactorCreateReqVO: {
      userId?: string
      factorType?: string
      displayName?: string
      keyId?: string
      verifiedAt?: string
      lastUsedAt?: string
      status?: number
    }
    MfaFactorRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      userId?: string
      factorType?: string
      displayName?: string
      keyId?: string
      verifiedAt?: string
      lastUsedAt?: string
      status?: number
    }
    MfaFactorUpdateReqVO: {
      version: number
      userId?: string
      factorType?: string
      displayName?: string
      keyId?: string
      verifiedAt?: string
      lastUsedAt?: string
      status?: number
    }
    OauthAccountCreateReqVO: {
      userId?: string
      provider?: string
      providerAppId?: string
      providerSubject?: string
      providerUnionId?: string
      providerName?: string
      providerAvatarUrl?: string
      lastUsedAt?: string
    }
    OauthAccountRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      userId?: string
      provider?: string
      providerAppId?: string
      providerSubject?: string
      providerUnionId?: string
      providerName?: string
      providerAvatarUrl?: string
      lastUsedAt?: string
    }
    OauthAccountUpdateReqVO: {
      version: number
      userId?: string
      provider?: string
      providerAppId?: string
      providerSubject?: string
      providerUnionId?: string
      providerName?: string
      providerAvatarUrl?: string
      lastUsedAt?: string
    }
    PageResult: {
      list?: Array<null>
      total: number
      pageNo: number
      pageSize: number
      pages: number
      hasNext: boolean
      hasPrevious: boolean
    }
    PasswordCredentialCreateReqVO: {
      userId?: string
      passwordChangedAt?: string
      resetRequired?: boolean
    }
    PasswordCredentialRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      userId?: string
      passwordChangedAt?: string
      resetRequired?: boolean
    }
    PasswordCredentialUpdateReqVO: {
      version: number
      userId?: string
      passwordChangedAt?: string
      resetRequired?: boolean
    }
    PermissionCreateReqVO: {
      permissionCode?: string
      permissionName?: string
      moduleCode?: string
      description?: string
    }
    PermissionRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      permissionCode?: string
      permissionName?: string
      moduleCode?: string
      description?: string
    }
    PermissionUpdateReqVO: {
      version: number
      permissionCode?: string
      permissionName?: string
      moduleCode?: string
      description?: string
    }
    PersonalAndBatchService_BatchExecuteInput: {
      action?: string
      nodeIds?: Array<string>
      targetSpaceId?: string
      targetParentId?: string
    }
    PersonalAndBatchService_BatchItemOutcome: {
      nodeId?: string
      ok: boolean
      message?: string
    }
    PersonalAndBatchService_BatchJobView: {
      id?: string
      jobType?: string
      status?: string
      totalCount: number
      successCount: number
      failedCount: number
      outcomes?: Array<components['schemas']["PersonalAndBatchService_BatchItemOutcome"]>
      errorMessage?: string
    }
    PersonalAndBatchService_FileProgressInput: {
      progressType?: string
      positionValue: number
    }
    PersonalAndBatchService_FileProgressView: {
      nodeId?: string
      progressType?: string
      positionValue: number
      updatedAt?: string
    }
    PersonalAndBatchService_PrivateNodeItem: {
      id?: string
      spaceId?: string
      spaceName?: string
      parentId?: string
      name?: string
      nodeType?: string
      currentVersionId?: string
      revision: number
      size: number
      mimeType?: string
      updatedAt?: string
    }
    PersonalAndBatchService_PrivateOverview: {
      spaces?: Array<components['schemas']["PersonalAndBatchService_PrivateSpaceItem"]>
      items?: Array<components['schemas']["PersonalAndBatchService_PrivateNodeItem"]>
    }
    PersonalAndBatchService_PrivateSpaceItem: {
      id?: string
      name?: string
      kind?: string
      color?: string
      privateEnabled: boolean
    }
    PersonalAndBatchService_RecentAccessItem: {
      id?: string
      spaceId?: string
      spaceName?: string
      parentId?: string
      name?: string
      nodeType?: string
      size: number
      mimeType?: string
      accessedAt?: string
    }
    PersonalAndBatchService_UnlockPrivateInput: {
      password?: string
      idleMinutes?: number
    }
    PersonalAndBatchService_UnlockPrivateResult: {
      privateToken?: string
      expiresAt?: string
      idleMinutes: number
    }
    PersonalAndBatchService_UserPreferences: {
      theme?: string
      density?: string
      fileView?: string
      fileSort?: string
      defaultSpaceId?: string
    }
    PersonalAndBatchService_ZipBatchInput: {
      nodeIds?: Array<string>
    }
    PreviewController_StreamTicketResponse: {
      assetType?: "ORIGINAL" | "VIDEO_MP4" | "VIDEO_HLS"
      mode?: "DIRECT" | "PROXY"
      expiresAt?: number
      proxyFallbackAllowed?: boolean
      ticket?: string | null
      streamUrl?: string
      expiresInSeconds: number
    }
    PreviewModels_ArchiveEntry: {
      path?: string
      name?: string
      directory: boolean
      size: number
      compressedSize: number
      modifiedAt?: string
    }
    PreviewModels_ArchiveListing: {
      format?: string
      totalEntries: number
      truncated: boolean
      entries?: Array<components['schemas']["PreviewModels_ArchiveEntry"]>
    }
    PreviewModels_ExcelMetadata: {
      sheets?: Array<components['schemas']["PreviewModels_ExcelSheetInfo"]>
    }
    PreviewModels_ExcelRowsPage: {
      sheetIndex: number
      sheetName?: string
      rowOffset: number
      rowLimit: number
      totalRows: number
      colCount: number
      hasMore: boolean
      rows?: Array<Array<string>>
    }
    PreviewModels_ExcelSheetInfo: {
      index: number
      name?: string
      rowCount: number
      colCount: number
    }
    PreviewModels_PreviewAsset: {
      id?: string
      fileId?: string
      versionId?: string
      assetType?: string
      status?: string
      mimeType?: string
      sizeBytes: number
      errorMessage?: string
      attemptCount: number
      updatedAt?: string
    }
    PreviewModels_VideoProbe: {
      videoTranscodeRequired?: boolean
      status?: "PENDING" | "PROCESSING" | "READY" | "FAILED"
      container?: string
      videoCodec?: string
      audioCodec?: string
      durationSeconds: number
      width: number
      height: number
      bitrate: number
      strategy?: string
      reason?: string
    }
    QrLoginController_QrStatusResp: {
      status?: string
      accessToken?: string
      expiresInSeconds?: number
      displayName?: string
    }
    QrLoginService_QrCreateResult: {
      qrId?: string
      expiresInSeconds: number
      expiresAt?: string
    }
    RecoveryCodeCreateReqVO: {
      userId?: string
      factorId?: string
      usedAt?: string
    }
    RecoveryCodeRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      userId?: string
      factorId?: string
      usedAt?: string
    }
    RecoveryCodeUpdateReqVO: {
      version: number
      userId?: string
      factorId?: string
      usedAt?: string
    }
    ResponseBodyEmitter: {
      timeout?: number
      handler?: components['schemas']["ResponseBodyEmitter_Handler"]
      earlySendAttempts?: Array<components['schemas']["ResponseBodyEmitter_DataWithMediaType"]>
      complete: boolean
      failure?: components['schemas']["Throwable"]
      timeoutCallback?: components['schemas']["ResponseBodyEmitter_DefaultCallback"]
      errorCallback?: components['schemas']["ResponseBodyEmitter_ErrorCallback"]
      completionCallback?: components['schemas']["ResponseBodyEmitter_DefaultCallback"]
      writeLock?: components['schemas']["Lock"]
    }
    ResponseBodyEmitter_DataWithMediaType: {
      data?: Record<string, unknown>
      mediaType?: components['schemas']["MediaType"]
    }
    ResponseBodyEmitter_DefaultCallback: {
      delegates?: Array<components['schemas']["Runnable"]>
      this$0?: components['schemas']["ResponseBodyEmitter"]
    }
    ResponseBodyEmitter_ErrorCallback: {
      delegates?: Array<components['schemas']["Consumer"]>
      this$0?: components['schemas']["ResponseBodyEmitter"]
    }
    ResponseBodyEmitter_Handler: Record<string, unknown>
    RoleCreateReqVO: {
      roleCode?: string
      roleName?: string
      description?: string
      enabled?: boolean
      isSystem?: boolean
    }
    RolePermissionCreateReqVO: {
      roleId?: string
      permissionId?: string
    }
    RolePermissionRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      roleId?: string
      permissionId?: string
    }
    RolePermissionUpdateReqVO: {
      version: number
      roleId?: string
      permissionId?: string
    }
    RoleRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      roleCode?: string
      roleName?: string
      description?: string
      enabled?: boolean
      isSystem?: boolean
    }
    RoleUpdateReqVO: {
      version: number
      roleCode?: string
      roleName?: string
      description?: string
      enabled?: boolean
      isSystem?: boolean
    }
    Runnable: Record<string, unknown>
    SearchModels_IndexStatus: {
      indexedChunks: number
      indexedFiles: number
      pendingEvents: number
      failedEvents: number
      meilisearchConfigured: boolean
      tikaConfigured: boolean
    }
    SearchModels_SearchHit: {
      id?: string
      spaceId?: string
      spaceName?: string
      parentId?: string
      name?: string
      nodeType?: string
      category?: string
      size: number
      mimeType?: string
      updatedAt?: string
      matchSource?: string
      snippet?: string
    }
    SearchModels_SearchResponse: {
      items?: Array<components['schemas']["SearchModels_SearchHit"]>
      total: number
      page: number
      pageSize: number
      hasMore: boolean
    }
    SecurityLogCreateReqVO: {
      userId?: string
      eventType?: string
      eventResult?: number
      sessionId?: string
      ipAddress?: string
      userAgent?: string
      detailJson?: string
      occurredAt?: string
    }
    SecurityLogRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      userId?: string
      eventType?: string
      eventResult?: number
      sessionId?: string
      ipAddress?: string
      userAgent?: string
      detailJson?: string
      occurredAt?: string
    }
    SecurityLogUpdateReqVO: {
      version: number
      userId?: string
      eventType?: string
      eventResult?: number
      sessionId?: string
      ipAddress?: string
      userAgent?: string
      detailJson?: string
      occurredAt?: string
    }
    SessionCreateReqVO: {
      userId?: string
      refreshExpiresAt?: string
      deviceId?: string
      deviceName?: string
      clientType?: string
      userAgent?: string
      ipAddress?: string
      lastUsedAt?: string
      lastRotatedAt?: string
      rotationCount?: number
      sessionVersion?: number
      revokedAt?: string
      revokeReason?: string
    }
    SessionRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      userId?: string
      refreshExpiresAt?: string
      deviceId?: string
      deviceName?: string
      clientType?: string
      userAgent?: string
      ipAddress?: string
      lastUsedAt?: string
      lastRotatedAt?: string
      rotationCount?: number
      sessionVersion?: number
      revokedAt?: string
      revokeReason?: string
    }
    SessionUpdateReqVO: {
      version: number
      userId?: string
      refreshExpiresAt?: string
      deviceId?: string
      deviceName?: string
      clientType?: string
      userAgent?: string
      ipAddress?: string
      lastUsedAt?: string
      lastRotatedAt?: string
      rotationCount?: number
      sessionVersion?: number
      revokedAt?: string
      revokeReason?: string
    }
    ShareLinkModels_CreatedShareLink: {
      id?: string
      token?: string
      urlPath?: string
      hasPassword: boolean
      allowDownload: boolean
      maxDownloads?: number
      expiresAt?: string
    }
    ShareLinkModels_CreateShareLinkInput: {
      password?: string
      expireDays?: number
      allowDownload?: boolean
      maxDownloads?: number
    }
    ShareLinkModels_PublicNode: {
      id?: string
      parentId?: string
      name?: string
      nodeType?: string
      size: number
      mimeType?: string
      updatedAt?: string
    }
    ShareLinkModels_PublicNodeList: {
      items?: Array<components['schemas']["ShareLinkModels_PublicNode"]>
    }
    ShareLinkModels_PublicShareMeta: {
      nodeId?: string
      nodeName?: string
      nodeType?: string
      size: number
      mimeType?: string
      ownerName?: string
      requiresPassword: boolean
      unlocked: boolean
      allowDownload: boolean
      expiresAt?: string
    }
    ShareLinkModels_SavePublicShareInput: {
      targetSpaceId?: string
      targetParentId: string
      nodeId?: string
      password?: string
    }
    ShareLinkModels_ShareLinkItem: {
      id?: string
      spaceId?: string
      nodeId?: string
      nodeName?: string
      nodeType?: string
      tokenPrefix?: string
      hasPassword: boolean
      allowDownload: boolean
      maxDownloads?: number
      downloadCount: number
      viewCount: number
      expiresAt?: string
      disabledAt?: string
      createdAt?: string
    }
    ShareLinkModels_VerifyPasswordInput: {
      password?: string
    }
    ShareModels_FolderShareInput: {
      target?: string
      permission?: string
    }
    ShareModels_InvitationItem: {
      id?: string
      targetType?: string
      spaceId?: string
      spaceName?: string
      nodeId?: string
      nodeName?: string
      inviterUserId?: string
      inviterName?: string
      inviteeUserId?: string
      inviteeName?: string
      role?: string
      status?: string
      message?: string
      createdAt?: string
    }
    ShareModels_InviteMemberInput: {
      target?: string
      role?: string
      message?: string
    }
    ShareModels_ResourceShareItem: {
      id?: string
      spaceId?: string
      spaceName?: string
      nodeId?: string
      nodeName?: string
      nodeType?: string
      ownerUserId?: string
      ownerName?: string
      targetUserId?: string
      targetName?: string
      permission?: string
      sourceType?: string
      createdAt?: string
    }
    ShareModels_SpaceMemberItem: {
      id?: string
      userId?: string
      displayName?: string
      email?: string
      role?: string
      joinedAt?: string
      expiresAt?: string
    }
    ShareModels_SpaceMembersOverview: {
      spaceId?: string
      kind?: string
      myRole?: string
      members?: Array<components['schemas']["ShareModels_SpaceMemberItem"]>
      pendingInvitations?: Array<components['schemas']["ShareModels_InvitationItem"]>
    }
    ShareModels_UpdateMemberRoleInput: {
      role?: string
    }
    SpaceCreateReqVO: {
      ownerUserId?: string
      kind?: string
      name?: string
      icon?: string
      color?: string
      description?: string
      status?: string
      viewerDownloadAllowed?: boolean
      viewerVersionReadAllowed?: boolean
      accessVersion?: number
      creationRequestKey?: string
      deleteRequestedAt?: string
      deletedAt?: string
      purgeAfter?: string
    }
    SpaceRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      ownerUserId?: string
      kind?: string
      name?: string
      icon?: string
      color?: string
      description?: string
      status?: string
      viewerDownloadAllowed?: boolean
      viewerVersionReadAllowed?: boolean
      accessVersion?: number
      creationRequestKey?: string
      deleteRequestedAt?: string
      deletedAt?: string
      purgeAfter?: string
    }
    SpaceUpdateReqVO: {
      version: number
      ownerUserId?: string
      kind?: string
      name?: string
      icon?: string
      color?: string
      description?: string
      status?: string
      viewerDownloadAllowed?: boolean
      viewerVersionReadAllowed?: boolean
      accessVersion?: number
      creationRequestKey?: string
      deleteRequestedAt?: string
      deletedAt?: string
      purgeAfter?: string
    }
    SseEmitter: {
      timeout?: number
      handler?: components['schemas']["ResponseBodyEmitter_Handler"]
      earlySendAttempts?: Array<components['schemas']["ResponseBodyEmitter_DataWithMediaType"]>
      complete: boolean
      failure?: components['schemas']["Throwable"]
      timeoutCallback?: components['schemas']["ResponseBodyEmitter_DefaultCallback"]
      errorCallback?: components['schemas']["ResponseBodyEmitter_ErrorCallback"]
      completionCallback?: components['schemas']["ResponseBodyEmitter_DefaultCallback"]
      writeLock?: components['schemas']["Lock"]
    }
    "StackTraceElement[]": Record<string, unknown>
    StreamingResponseBody: Record<string, unknown>
    Throwable: {
      detailMessage?: string
      cause?: components['schemas']["Throwable"]
      stackTrace?: components['schemas']["StackTraceElement[]"]
      suppressedExceptions?: Array<components['schemas']["Throwable"]>
    }
    UserCreateReqVO: {
      displayName?: string
      avatarUrl?: string
      status?: number
      registerSource?: string
      registeredAt?: string
      lastLoginAt?: string
    }
    UserLoginRespVO: {
      accessToken?: string
      refreshToken?: string
      expiresIn?: number
      tokenType?: string
      user?: components['schemas']["UserRespVO"]
      userProfile?: components['schemas']["UserProfileRespVO"]
      userSetting?: components['schemas']["UserSettingRespVO"]
    }
    UserPreferenceCreateReqVO: {
      userId?: string
      spaceId?: string
      sortOrder?: number
      treeCollapsed?: boolean
    }
    UserPreferenceRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      userId?: string
      spaceId?: string
      sortOrder?: number
      treeCollapsed?: boolean
    }
    UserPreferenceUpdateReqVO: {
      version: number
      userId?: string
      spaceId?: string
      sortOrder?: number
      treeCollapsed?: boolean
    }
    UserProfileCreateReqVO: {
      userId?: string
      bio?: string
      websiteUrl?: string
      coverUrl?: string
    }
    UserProfileRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      userId?: string
      bio?: string
      websiteUrl?: string
      coverUrl?: string
    }
    UserProfileUpdateReqVO: {
      version: number
      userId?: string
      bio?: string
      websiteUrl?: string
      coverUrl?: string
    }
    UserRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      displayName?: string
      avatarUrl?: string
      status?: number
      registerSource?: string
      registeredAt?: string
      lastLoginAt?: string
    }
    UserRoleCreateReqVO: {
      userId?: string
      roleId?: string
      expiresAt?: string
    }
    UserRoleRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      userId?: string
      roleId?: string
      expiresAt?: string
    }
    UserRoleUpdateReqVO: {
      version: number
      userId?: string
      roleId?: string
      expiresAt?: string
    }
    UserSecurityCreateReqVO: {
      userId?: string
      failedLoginCount?: number
      lockedUntil?: string
      passwordLoginEnabled?: boolean
      sessionVersion?: number
      mfaRequired?: boolean
      lastPasswordLoginAt?: string
    }
    UserSecurityRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      userId?: string
      failedLoginCount?: number
      lockedUntil?: string
      passwordLoginEnabled?: boolean
      sessionVersion?: number
      mfaRequired?: boolean
      lastPasswordLoginAt?: string
    }
    UserSecurityUpdateReqVO: {
      version: number
      userId?: string
      failedLoginCount?: number
      lockedUntil?: string
      passwordLoginEnabled?: boolean
      sessionVersion?: number
      mfaRequired?: boolean
      lastPasswordLoginAt?: string
    }
    UserSettingCreateReqVO: {
      userId?: string
      theme?: string
      locale?: string
      timezone?: string
      density?: string
      fileView?: string
      fileSort?: string
      showHiddenFiles?: boolean
      defaultSpaceId?: string
      notificationSettings?: string
    }
    UserSettingRespVO: {
      id?: string
      createdAt?: string
      updatedAt?: string
      version?: number
      userId?: string
      theme?: string
      locale?: string
      timezone?: string
      density?: string
      fileView?: string
      fileSort?: string
      showHiddenFiles?: boolean
      defaultSpaceId?: string
      notificationSettings?: string
    }
    UserSettingUpdateReqVO: {
      version: number
      userId?: string
      theme?: string
      locale?: string
      timezone?: string
      density?: string
      fileView?: string
      fileSort?: string
      showHiddenFiles?: boolean
      defaultSpaceId?: string
      notificationSettings?: string
    }
    UserUpdateReqVO: {
      version: number
      displayName?: string
      avatarUrl?: string
      status?: number
      registerSource?: string
      registeredAt?: string
      lastLoginAt?: string
    }
    VersionPinUndoService_BatchRenameApplyResult: {
      renamedCount: number
      skippedCount: number
      undo?: components['schemas']["VersionPinUndoService_UndoTokenReceipt"]
    }
    VersionPinUndoService_BatchRenamePreviewItem: {
      nodeId?: string
      oldName?: string
      newName?: string
      conflict: boolean
      reason?: string
    }
    VersionPinUndoService_BatchRenameRequest: {
      nodeIds?: Array<string>
      mode?: string
      prefix?: string
      suffix?: string
      find?: string
      replace?: string
      template?: string
      startNumber?: number
      keepExtension?: boolean
      conflictPolicy?: string
    }
    VersionPinUndoService_IssueUndoInput: {
      spaceId: string
      actionType?: string
      summary?: string
      nodeId: string
      oldName?: string
      oldParentId?: string
    }
    VersionPinUndoService_QuickAccessItem: {
      id?: string
      spaceId?: string
      spaceName?: string
      nodeId?: string
      name?: string
      nodeType?: string
      size: number
      updatedAt?: string
    }
    VersionPinUndoService_UndoTokenReceipt: {
      tokenId?: string
      actionType?: string
      summary?: string
      expiresAt?: string
    }
    VersionPinUndoService_UpdateUserVersionPolicyInput: {
      maxVersions: number
    }
    VersionPinUndoService_UserVersionPolicy: {
      maxVersions: number
      systemMaxVersions: number
    }
    VideoStreamingService_State: {
      status?: string
      availableSeconds: number
      errorMessage?: string
    }
    WebSessionService_DeviceSession: {
      id?: string
      deviceName?: string
      clientType?: string
      ipAddress?: string
      createdAt?: string
      lastUsedAt?: string
      expiresAt?: string
      current: boolean
    }
  }
}

export interface paths {
  "/api/admin/auth/email": {
    post: {
      requestBody: components['schemas']["EmailRegisterReqVO"]
      response: {
        code: number
        msg: string
        data?: boolean
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/login-log": {
    post: {
      requestBody: components['schemas']["LoginLogCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/login-log/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["LoginLogRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["LoginLogUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/login-log/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/login/email": {
    post: {
      requestBody: components['schemas']["EmailLoginReqVO"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["UserLoginRespVO"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/mfa-factor": {
    post: {
      requestBody: components['schemas']["MfaFactorCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/mfa-factor/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["MfaFactorRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["MfaFactorUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/mfa-factor/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/oauth-account": {
    post: {
      requestBody: components['schemas']["OauthAccountCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/oauth-account/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["OauthAccountRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["OauthAccountUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/oauth-account/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/password-credential": {
    post: {
      requestBody: components['schemas']["PasswordCredentialCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/password-credential/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["PasswordCredentialRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["PasswordCredentialUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/password-credential/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/password/reset": {
    post: {
      requestBody: components['schemas']["AuthController_ResetPasswordReq"]
      response: {
        code: number
        msg: string
        data?: boolean
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/password/send-code": {
    post: {
      parameters: {
        query?: {
          email: string
        }
      }
      response: {
        code: number
        msg: string
        data?: boolean
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/permission": {
    post: {
      requestBody: components['schemas']["PermissionCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/permission/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["PermissionRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["PermissionUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/permission/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/qr/{qrId}/status": {
    get: {
      parameters: {
        path: {
          qrId: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["QrLoginController_QrStatusResp"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/qr/create": {
    post: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["QrLoginService_QrCreateResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/recovery-code": {
    post: {
      requestBody: components['schemas']["RecoveryCodeCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/recovery-code/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["RecoveryCodeRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["RecoveryCodeUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/recovery-code/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/register/send-email-code": {
    post: {
      parameters: {
        query?: {
          email: string
        }
      }
      response: {
        code: number
        msg: string
        data?: boolean
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/role": {
    post: {
      requestBody: components['schemas']["RoleCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/role-permission": {
    post: {
      requestBody: components['schemas']["RolePermissionCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/role-permission/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["RolePermissionRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["RolePermissionUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/role-permission/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/role/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["RoleRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["RoleUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/role/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/security-log": {
    post: {
      requestBody: components['schemas']["SecurityLogCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/security-log/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["SecurityLogRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["SecurityLogUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/security-log/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/session": {
    post: {
      requestBody: components['schemas']["SessionCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/session/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["SessionRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["SessionUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/session/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/user-role": {
    post: {
      requestBody: components['schemas']["UserRoleCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/user-role/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["UserRoleRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["UserRoleUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/user-role/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/user-security": {
    post: {
      requestBody: components['schemas']["UserSecurityCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/user-security/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["UserSecurityRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["UserSecurityUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/auth/user-security/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/space/member": {
    post: {
      requestBody: components['schemas']["MemberCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/space/member/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["MemberRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["MemberUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/space/member/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/space/space": {
    post: {
      requestBody: components['schemas']["SpaceCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/space/space/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["SpaceRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["SpaceUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/space/space/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/space/user-preference": {
    post: {
      requestBody: components['schemas']["UserPreferenceCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/space/user-preference/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["UserPreferenceRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["UserPreferenceUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/space/user-preference/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/sys/user": {
    post: {
      requestBody: components['schemas']["UserCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/sys/user-profile": {
    post: {
      requestBody: components['schemas']["UserProfileCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/sys/user-profile/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["UserProfileRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["UserProfileUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/sys/user-profile/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/sys/user-setting": {
    post: {
      requestBody: components['schemas']["UserSettingCreateReqVO"]
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/admin/sys/user-setting/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["UserSettingRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["UserSettingUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/sys/user-setting/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/admin/sys/user/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["UserRespVO"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["UserUpdateReqVO"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/admin/sys/user/page": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PageResult"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/error-reports": {
    get: {
      parameters: {
        query?: {
          resolved?: boolean
          level?: string
          limit?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/error-reports/{id}/resolve": {
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/error-reports/resolved": {
    delete: {
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/jobs": {
    get: {
      parameters: {
        query?: {
          status?: string
          consumerGroup?: string
        }
      }
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/jobs/{id}/retry": {
    post: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/logs": {
    get: {
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/messages": {
    get: {
      response: {
        code: number
        msg: string
        data?: Array<components['schemas']["MessageModels_AdminMessageRecord"]>
        traceId?: string | null
      }
    }
    post: {
      requestBody: components['schemas']["MessageModels_PublishMessageInput"]
      response: {
        code: number
        msg: string
        data?: string
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/oauth-providers/{code}": {
    put: {
      parameters: {
        path: {
          code: string
        }
      }
      requestBody: components['schemas']["AdminController_OAuthProviderInput"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/search/consistency": {
    get: {
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/search/rebuild": {
    post: {
      parameters: {
        query?: {
          spaceId?: number
          nodeId?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/search/status": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["SearchModels_IndexStatus"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/settings/auth": {
    get: {
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
    put: {
      requestBody: components['schemas']["AdminController_AuthAndSmtpInput"]
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/settings/files": {
    get: {
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
    put: {
      requestBody: components['schemas']["AdminController_FilePolicyInput"]
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/settings/site": {
    get: {
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
    put: {
      requestBody: components['schemas']["AdminController_SiteSettingsInput"]
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/stats": {
    get: {
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/storage-profiles": {
    get: {
      response: {
        code: number
        msg: string
        data?: Array<Record<string, unknown>>
        traceId?: string | null
      }
    }
    post: {
      requestBody: components['schemas']["AdminController_StorageProfileInput"]
      response: {
        code: number
        msg: string
        data?: string
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/storage-profiles/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["AdminController_StorageProfileInput"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/storage-profiles/{id}/default": {
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/storage-profiles/{id}/test": {
    post: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/storage-profiles/test-config": {
    post: {
      requestBody: components['schemas']["AdminController_StorageProfileTestInput"]
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/storage-routing": {
    get: {
      response: {
        code: number
        msg: string
        data?: Record<string, string>
        traceId?: string | null
      }
    }
    put: {
      requestBody: components['schemas']["AdminController_StorageRoutingInput"]
      response: {
        code: number
        msg: string
        data?: Record<string, string>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/storage/gc": {
    post: {
      parameters: {
        query?: {
          dryRun?: boolean
          graceMinutes?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/storage/purge-trash": {
    post: {
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/tasks": {
    get: {
      parameters: {
        query?: {
          status?: string
        }
      }
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/tasks/cleanup-expired": {
    post: {
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/users": {
    get: {
      parameters: {
        query?: {
          page?: number
          pageSize?: number
          query?: string
          status?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: Array<Record<string, unknown>>
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/users/{id}/status": {
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      requestBody: components['schemas']["AdminController_Status"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/admin/users/count": {
    get: {
      parameters: {
        query?: {
          query?: string
          status?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/v1/assets/images": {
    post: {
      parameters: {
        query?: {
          category?: string
        }
      }
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/auth/logout": {
    post: {
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/auth/oauth/{code}/authorize": {
    get: {
      parameters: {
        path: {
          code: string
        }
      }
      response: null
    }
  }
  "/api/v1/auth/oauth/{code}/callback": {
    get: {
      parameters: {
        path: {
          code: string
        }
        query?: {
          state: string
          code: string
        }
      }
      response: null
    }
  }
  "/api/v1/auth/qr/{qrId}/confirm": {
    post: {
      parameters: {
        path: {
          qrId: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/auth/qr/{qrId}/scan": {
    post: {
      parameters: {
        path: {
          qrId: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/auth/refresh": {
    post: {
      requestBody: Record<string, unknown>
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/auth/sessions": {
    get: {
      response: {
        code: number
        msg: string
        data?: Array<components['schemas']["WebSessionService_DeviceSession"]>
        traceId?: string | null
      }
    }
  }
  "/api/v1/auth/sessions/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/batch/jobs/{jobId}/download": {
    get: {
      parameters: {
        path: {
          jobId: string
        }
      }
      response: Blob
    }
  }
  "/api/v1/me": {
    get: {
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
    put: {
      requestBody: components['schemas']["AccountController_ProfileInput"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/me/password": {
    put: {
      requestBody: components['schemas']["AccountController_ChangePasswordInput"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/me/preferences": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PersonalAndBatchService_UserPreferences"]
        traceId?: string | null
      }
    }
    put: {
      requestBody: components['schemas']["PersonalAndBatchService_UserPreferences"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["PersonalAndBatchService_UserPreferences"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/me/private/lock": {
    post: {
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/me/private/overview": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["PersonalAndBatchService_PrivateOverview"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/me/private/spaces/{space}": {
    put: {
      parameters: {
        path: {
          space: string
        }
        query?: {
          enabled: boolean
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/me/private/unlock": {
    post: {
      requestBody: components['schemas']["PersonalAndBatchService_UnlockPrivateInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["PersonalAndBatchService_UnlockPrivateResult"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/me/verify-password": {
    post: {
      requestBody: components['schemas']["AccountController_VerifyPasswordInput"]
      response: {
        code: number
        msg: string
        data?: boolean
        traceId?: string | null
      }
    }
  }
  "/api/v1/me/version-policy": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["VersionPinUndoService_UserVersionPolicy"]
        traceId?: string | null
      }
    }
    put: {
      requestBody: components['schemas']["VersionPinUndoService_UpdateUserVersionPolicyInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["VersionPinUndoService_UserVersionPolicy"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/media/hls": {
    get: {
      parameters: {
        query?: {
          ticket: string
          file: string
        }
      }
      response: null
    }
    head: {
      parameters: {
        query?: {
          ticket: string
          file: string
        }
      }
      response: null
    }
  }
  "/api/v1/media/stream": {
    get: {
      parameters: {
        query?: {
          ticket: string
        }
      }
      response: components['schemas']["StreamingResponseBody"]
    }
  }
  "/api/v1/media/timeline": {
    get: {
      parameters: {
        query?: {
          spaceId?: number
          mediaType?: string
          page?: number
          pageSize?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["MediaTimelineService_TimelinePage"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/messages": {
    get: {
      response: {
        code: number
        msg: string
        data?: components['schemas']["MessageModels_InboxResponse"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/messages/{id}/read": {
    put: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/messages/events": {
    get: {
      response: components['schemas']["SseEmitter"]
    }
  }
  "/api/v1/messages/read-all": {
    post: {
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/public/assets/{id}": {
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: Blob
    }
  }
  "/api/v1/public/shares/{token}": {
    get: {
      parameters: {
        path: {
          token: string
        }
        query?: {
          password?: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["ShareLinkModels_PublicShareMeta"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/public/shares/{token}/content": {
    get: {
      parameters: {
        path: {
          token: string
        }
        query?: {
          password?: string
          nodeId?: number
          download?: boolean
        }
      }
      response: Blob
    }
  }
  "/api/v1/public/shares/{token}/nodes": {
    get: {
      parameters: {
        path: {
          token: string
        }
        query?: {
          password?: string
          parentId?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["ShareLinkModels_PublicNodeList"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/public/shares/{token}/save": {
    post: {
      parameters: {
        path: {
          token: string
        }
      }
      requestBody: components['schemas']["ShareLinkModels_SavePublicShareInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_Node"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/public/shares/{token}/verify": {
    post: {
      parameters: {
        path: {
          token: string
        }
      }
      requestBody: components['schemas']["ShareLinkModels_VerifyPasswordInput"]
      response: {
        code: number
        msg: string
        data?: boolean
        traceId?: string | null
      }
    }
  }
  "/api/v1/public/site-config": {
    get: {
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/quick-access": {
    get: {
      response: {
        code: number
        msg: string
        data?: Array<components['schemas']["VersionPinUndoService_QuickAccessItem"]>
        traceId?: string | null
      }
    }
  }
  "/api/v1/recent": {
    get: {
      response: {
        code: number
        msg: string
        data?: Array<components['schemas']["PersonalAndBatchService_RecentAccessItem"]>
        traceId?: string | null
      }
    }
  }
  "/api/v1/search": {
    get: {
      parameters: {
        query?: {
          query?: string
          spaceId?: number
          category?: string
          extension?: string
          includeContent?: boolean
          page?: number
          pageSize?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["SearchModels_SearchResponse"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/share-links": {
    get: {
      response: {
        code: number
        msg: string
        data?: Array<components['schemas']["ShareLinkModels_ShareLinkItem"]>
        traceId?: string | null
      }
    }
  }
  "/api/v1/share-links/{id}/disable": {
    post: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/shares/by-me": {
    get: {
      response: {
        code: number
        msg: string
        data?: Array<components['schemas']["ShareModels_ResourceShareItem"]>
        traceId?: string | null
      }
    }
  }
  "/api/v1/shares/events": {
    get: {
      response: components['schemas']["SseEmitter"]
    }
  }
  "/api/v1/shares/invitations": {
    get: {
      response: {
        code: number
        msg: string
        data?: Array<components['schemas']["ShareModels_InvitationItem"]>
        traceId?: string | null
      }
    }
  }
  "/api/v1/shares/invitations/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/shares/invitations/{id}/accept": {
    post: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/shares/invitations/{id}/reject": {
    post: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/shares/resources/{shareId}": {
    delete: {
      parameters: {
        path: {
          shareId: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/shares/with-me": {
    get: {
      response: {
        code: number
        msg: string
        data?: Array<components['schemas']["ShareModels_ResourceShareItem"]>
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces": {
    get: {
      response: {
        code: number
        msg: string
        data?: Array<components['schemas']["DriveModels_Space"]>
        traceId?: string | null
      }
    }
    post: {
      requestBody: components['schemas']["DriveModels_SpaceInput"]
      response: {
        code: number
        msg: string
        data?: string
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}": {
    delete: {
      parameters: {
        path: {
          space: string
        }
        query?: {
          confirmation: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          space: string
        }
      }
      requestBody: components['schemas']["DriveModels_SpaceInput"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/batch/execute": {
    post: {
      parameters: {
        path: {
          space: string
        }
      }
      requestBody: components['schemas']["PersonalAndBatchService_BatchExecuteInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["PersonalAndBatchService_BatchJobView"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/batch/zip": {
    post: {
      parameters: {
        path: {
          space: string
        }
      }
      requestBody: components['schemas']["PersonalAndBatchService_ZipBatchInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["PersonalAndBatchService_BatchJobView"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/convert-personal": {
    post: {
      parameters: {
        path: {
          space: string
        }
        query?: {
          keepMembers?: boolean
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/convert-shared": {
    post: {
      parameters: {
        path: {
          space: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/files": {
    post: {
      parameters: {
        path: {
          space: string
        }
        query?: {
          parentId?: number
          replaceId?: number
          revision?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_Node"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/folders": {
    post: {
      parameters: {
        path: {
          space: string
        }
      }
      requestBody: components['schemas']["DriveModels_NodeInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_Node"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/invitations": {
    post: {
      parameters: {
        path: {
          space: string
        }
      }
      requestBody: components['schemas']["ShareModels_InviteMemberInput"]
      response: {
        code: number
        msg: string
        data?: string
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/members": {
    get: {
      parameters: {
        path: {
          space: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["ShareModels_SpaceMembersOverview"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/members/{memberUserId}": {
    delete: {
      parameters: {
        path: {
          space: string
          memberUserId: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          space: string
          memberUserId: string
        }
      }
      requestBody: components['schemas']["ShareModels_UpdateMemberRoleInput"]
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes": {
    get: {
      parameters: {
        path: {
          space: string
        }
        query?: {
          parentId?: number
          page?: number
          pageSize?: number
          folders?: boolean
          trash?: boolean
          query?: string
          sort?: string
          hidden?: boolean
          favorites?: boolean
          category?: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_Page"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}": {
    delete: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          revision: number
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    patch: {
      parameters: {
        path: {
          space: string
          id: string
        }
      }
      requestBody: components['schemas']["DriveModels_ChangeInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_Node"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/access": {
    post: {
      parameters: {
        path: {
          space: string
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/ancestors": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: Array<components['schemas']["DriveModels_Node"]>
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/content": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          versionId?: number
          download?: boolean
        }
      }
      response: Blob
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/favorite": {
    put: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          enabled: boolean
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/hidden": {
    put: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          enabled: boolean
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/pin": {
    put: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          enabled: boolean
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/preview/archive/entries": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          versionId?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["PreviewModels_ArchiveListing"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/preview/archive/extract": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          versionId?: number
          entryPath: string
        }
      }
      response: Blob
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/preview/assets/{assetType}": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
          assetType: string
        }
        query?: {
          versionId?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["PreviewModels_PreviewAsset"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/preview/assets/{assetType}/content": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
          assetType: string
        }
        query?: {
          versionId?: number
        }
      }
      response: Blob
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/preview/assets/{assetType}/retry": {
    post: {
      parameters: {
        path: {
          space: string
          id: string
          assetType: string
        }
        query?: {
          versionId?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["PreviewModels_PreviewAsset"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/preview/excel/rows": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          versionId?: number
          sheetIndex?: number
          rowOffset?: number
          rowLimit?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["PreviewModels_ExcelRowsPage"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/preview/excel/sheets": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          versionId?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["PreviewModels_ExcelMetadata"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/preview/stream-ticket": {
    post: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          versionId?: number
          assetType?: string
          forceProxy?: boolean
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["PreviewController_StreamTicketResponse"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/preview/thumbnail": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          versionId?: number
          width?: number
        }
      }
      response: Blob
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/preview/video/probe": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          versionId?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["PreviewModels_VideoProbe"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/preview/video/session": {
    post: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          versionId?: number
          retry?: boolean
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["VideoStreamingService_State"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/progress": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["PersonalAndBatchService_FileProgressView"]
        traceId?: string | null
      }
    }
    put: {
      parameters: {
        path: {
          space: string
          id: string
        }
      }
      requestBody: components['schemas']["PersonalAndBatchService_FileProgressInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["PersonalAndBatchService_FileProgressView"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/restore": {
    post: {
      parameters: {
        path: {
          space: string
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/share-links": {
    post: {
      parameters: {
        path: {
          space: string
          id: string
        }
      }
      requestBody: components['schemas']["ShareLinkModels_CreateShareLinkInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["ShareLinkModels_CreatedShareLink"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/shares": {
    post: {
      parameters: {
        path: {
          space: string
          id: string
        }
      }
      requestBody: components['schemas']["ShareModels_FolderShareInput"]
      response: {
        code: number
        msg: string
        data?: string
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/text": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
        }
        query?: {
          versionId?: number
          offset?: number
          limit?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_TextChunk"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/transfer": {
    post: {
      parameters: {
        path: {
          space: string
          id: string
        }
      }
      requestBody: components['schemas']["DriveModels_TransferInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_Node"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/versions": {
    get: {
      parameters: {
        path: {
          space: string
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: Array<components['schemas']["DriveModels_Version"]>
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/{id}/versions/{version}/restore": {
    post: {
      parameters: {
        path: {
          space: string
          id: string
          version: string
        }
        query?: {
          revision: number
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_Node"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/batch-rename/apply": {
    post: {
      parameters: {
        path: {
          space: string
        }
      }
      requestBody: components['schemas']["VersionPinUndoService_BatchRenameRequest"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["VersionPinUndoService_BatchRenameApplyResult"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/nodes/batch-rename/preview": {
    post: {
      parameters: {
        path: {
          space: string
        }
      }
      requestBody: components['schemas']["VersionPinUndoService_BatchRenameRequest"]
      response: {
        code: number
        msg: string
        data?: Array<components['schemas']["VersionPinUndoService_BatchRenamePreviewItem"]>
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/private/lock": {
    post: {
      parameters: {
        path: {
          space: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/private/unlock": {
    post: {
      parameters: {
        path: {
          space: string
        }
      }
      requestBody: components['schemas']["PersonalAndBatchService_UnlockPrivateInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["PersonalAndBatchService_UnlockPrivateResult"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/stats": {
    get: {
      parameters: {
        path: {
          space: string
        }
        query?: {
          parentId?: number
          hidden?: boolean
          includeSubdirs?: boolean
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_DirectoryStats"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/trash": {
    delete: {
      parameters: {
        path: {
          space: string
        }
      }
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/v1/spaces/{space}/trash/{id}": {
    delete: {
      parameters: {
        path: {
          space: string
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: number
        traceId?: string | null
      }
    }
  }
  "/api/v1/undo/{tokenId}": {
    post: {
      parameters: {
        path: {
          tokenId: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/undo/issue": {
    post: {
      requestBody: components['schemas']["VersionPinUndoService_IssueUndoInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["VersionPinUndoService_UndoTokenReceipt"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/uploads": {
    get: {
      response: {
        code: number
        msg: string
        data?: Array<Record<string, unknown>>
        traceId?: string | null
      }
    }
    post: {
      requestBody: components['schemas']["DriveModels_UploadInput"]
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_Upload"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/uploads/{id}": {
    delete: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
    get: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_Upload"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/uploads/{id}/chunks/{index}": {
    put: {
      parameters: {
        path: {
          id: string
          index: number
        }
        query?: {
          sha256?: string
        }
      }
      response: {
        code: number
        msg: string
        data?: null
        traceId?: string | null
      }
    }
  }
  "/api/v1/uploads/{id}/complete": {
    post: {
      parameters: {
        path: {
          id: string
        }
        query?: {
          expectedSha256?: string
        }
      }
      response: {
        code: number
        msg: string
        data?: string
        traceId?: string | null
      }
    }
  }
  "/api/v1/uploads/{id}/direct": {
    post: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/uploads/{id}/direct/parts/{index}": {
    get: {
      parameters: {
        path: {
          id: string
          index: number
        }
      }
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
  "/api/v1/uploads/{id}/fallback": {
    post: {
      parameters: {
        path: {
          id: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_Upload"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/uploads/{id}/finalize": {
    post: {
      parameters: {
        path: {
          id: string
        }
        query?: {
          expectedSha256?: string
        }
      }
      response: {
        code: number
        msg: string
        data?: components['schemas']["DriveModels_Upload"]
        traceId?: string | null
      }
    }
  }
  "/api/v1/uploads/{id}/presign": {
    get: {
      parameters: {
        path: {
          id: string
        }
        query?: {
          expiresSeconds?: number
        }
      }
      response: {
        code: number
        msg: string
        data?: Record<string, unknown>
        traceId?: string | null
      }
    }
  }
}

export type ApiSchemas = components['schemas']
export type ApiPaths = paths

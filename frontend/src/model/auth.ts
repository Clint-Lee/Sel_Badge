/** Badge 现有身份接口的公共用户资料，不包含密码和令牌。 */
export interface BadgeUser {
  id: number;
  username: string;
  email: string;
  role: string;
  status: string;
  avatarUrl: string | null;
  phoneMasked: string | null;
  createdAt: string;
}

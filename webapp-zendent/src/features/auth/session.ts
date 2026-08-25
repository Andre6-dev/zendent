/**
 * Who is signed in, as the shell shows them.
 *
 * Lives here rather than beside the server code that fetches it so that a
 * component importing the type cannot, by one careless edit, come to import the
 * module that touches the API's tokens. Names and roles are all a screen needs;
 * the identifiers `/me` also returns are deliberately not carried across.
 */
export interface SignedInMember {
  memberName: string
  clinicName: string
  roles: Array<string>
}

/**
 * The role to show beside a name. The API answers with codes — `ADMIN`,
 * `DENTIST` — and the first is the one a Clinic would introduce someone by.
 */
export function primaryRoleOf(member: SignedInMember): string | undefined {
  // Asked of the array rather than of the element: TypeScript types an index
  // read as always present, and an empty list is exactly the case worth
  // handling here.
  if (member.roles.length === 0) {
    return undefined
  }
  const role = member.roles[0]
  return role.charAt(0) + role.slice(1).toLowerCase()
}

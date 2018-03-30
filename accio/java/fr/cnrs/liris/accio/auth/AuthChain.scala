/*
 * Accio is a platform to launch computer science experiments.
 * Copyright (C) 2016-2018 Vincent Primault <v.primault@ucl.ac.uk>
 *
 * Accio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Accio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Accio.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.cnrs.liris.accio.auth

import com.twitter.util.Future
import fr.cnrs.liris.accio.api.UserInfo

/**
 * Tries to determine whether a given client identifier is allowed by using a sequence of
 * authentication strategies, with optional support for anonymous users. It will try all strategies
 * in order, and use the result of the first one that is positive. If at the end no decision has
 * been made, the user will be authenticated as anonymous if anonymous users are allowed, or
 * rejected otherwise.
 *
 * @param strategies     List of authentication strategies to try.
 * @param allowAnonymous Whether anonymous users are allowed.
 */
final class AuthChain(strategies: Seq[AuthStrategy], allowAnonymous: Boolean) {
  /**
   * Try to authenticate a user, given a client identifier.
   *
   * @param credentials Client identifier.
   */
  def authenticate(credentials: Option[String]): Future[Option[UserInfo]] = {
    credentials match {
      case Some(clientId) => authenticate(strategies.iterator, clientId)
      case None => Future.value(if (allowAnonymous) Some(UserInfo.Anonymous) else None)
    }
  }

  private def authenticate(it: Iterator[AuthStrategy], clientId: String): Future[Option[UserInfo]] = {
    if (it.hasNext) {
      it.next.authenticate(clientId).flatMap {
        case Some(userInfo) =>
          Future.value(Some(userInfo.copy(groups = userInfo.groups + "system:authenticated")))
        case None => authenticate(it, clientId)
      }
    } else if (allowAnonymous) {
      Future.value(Some(UserInfo.Anonymous))
    } else {
      Future.value(None)
    }
  }
}
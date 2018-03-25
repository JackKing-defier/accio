/*
 * Accio is a program whose purpose is to study location privacy.
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

import React from 'react'
import {toPairs} from 'lodash'
import autobind from 'autobind-decorator'
import xhr from '../../../utils/xhr'
import WorkflowList from './WorkflowList'

class WorkflowListContainer extends React.Component {
  constructor(props) {
    super(props)
    this.state = {
      data: {results: null, total_count: 0},
      query: {},
      page: 1,
    }
  }

  _loadData(state) {
    const qs = toPairs(state.query).map(pair => pair[0] + '=' + encodeURIComponent(pair[1])).join('&')
    xhr('/api/v1/workflow?per_page=25&page=' + state.page + '&' + qs)
      .then(data => this.setState(Object.assign(state, {data: data})));
  }

  @autobind
  _handleChange(state) {
    this._loadData(state)
  }

  componentDidMount() {
    this._loadData(this.state)
  }

  render() {
    return (
      <WorkflowList
        page={this.state.page}
        query={this.state.query}
        workflows={this.state.data.results}
        totalCount={this.state.data.total_count}
        onChange={this._handleChange}/>
    )
  }
}

export default WorkflowListContainer

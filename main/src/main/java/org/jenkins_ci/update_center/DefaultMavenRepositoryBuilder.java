/**
 * The MIT License
 *
 * Copyright (c) 2011, Jerome Lacoste
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkins_ci.update_center;

import org.jenkins_ci.update_center.repo.ArtifactoryRepositoryImpl;
import org.jenkins_ci.update_center.repo.MavenRepository;
import org.jenkins_ci.update_center.repo.NexusRepositoryImpl;

public class DefaultMavenRepositoryBuilder {
    private MavenRepository instance = null;

    public DefaultMavenRepositoryBuilder() throws Exception {
        this(null);
    }

    public DefaultMavenRepositoryBuilder(String repoImpl) throws Exception {
        if ("nexus".equals(repoImpl)) {
            instance = new NexusRepositoryImpl();
        } else {
            instance = new ArtifactoryRepositoryImpl();
        }
    }

    public DefaultMavenRepositoryBuilder withMaxPlugins(Integer maxPlugins) {
        instance.setMaxPlugins(maxPlugins);
        return this;
    }

    public DefaultMavenRepositoryBuilder withCredentials(String username, String password) {
        instance.setCredentials(username, password);
        return this;
    }

    public MavenRepository getInstance() {
        return instance;
    }

    public static MavenRepository createStandardInstance() throws Exception {
        return new DefaultMavenRepositoryBuilder().instance;
    }
}
